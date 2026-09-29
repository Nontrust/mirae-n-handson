package com.example.item;

import jakarta.persistence.criteria.Join;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문항 검색. legacy {@code search.php}(buildSearchQuery + runSearchQuery)를 그대로 옮긴 것이다.
 * 형식이 안 맞거나 존재하지 않는 값(단원 코드, 태그, 난이도, 페이지, 정렬 기준)도 요청을 막지 않고
 * 경고를 모아 {@code message} 에 담아 0건으로 응답한다 — 404/400 을 내지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class ItemSearchService {

    private static final Logger log = LoggerFactory.getLogger(ItemSearchService.class);

    private static final int PAGE_SIZE = 20;
    private static final int MAX_PAGE = 999;
    private static final int KEYWORD_MAX_LENGTH = 100;
    private static final int TAG_MAX_LENGTH = 50;

    private static final Pattern UNIT_CODE_PATTERN = Pattern.compile("^[A-Za-z][0-9]{1,2}-[0-9]{1,2}$");
    private static final Pattern LEVEL_EXACT_PATTERN = Pattern.compile("^[1-5]$");
    private static final Pattern PAGE_DIGITS_PATTERN = Pattern.compile("^[0-9]+$");
    private static final Pattern LEADING_INT_PATTERN = Pattern.compile("^[+-]?\\d+");

    private final ItemRepository itemRepository;
    private final UnitRepository unitRepository;
    private final TagRepository tagRepository;

    public ItemSearchService(ItemRepository itemRepository, UnitRepository unitRepository, TagRepository tagRepository) {
        this.itemRepository = itemRepository;
        this.unitRepository = unitRepository;
        this.tagRepository = tagRepository;
    }

    public ItemSearchResponse search(String q, String unit, String level, String tag, String sort, String dir, String page) {
        List<String> warnings = new ArrayList<>();

        Specification<Item> spec = statusActive()
            .and(keywordFilter(q, warnings))
            .and(unitFilter(unit, warnings))
            .and(levelFilter(level, warnings))
            .and(tagFilter(tag, warnings));

        Sort resolvedSort = resolveSort(sort, dir, warnings);
        int pageNumber = resolvePage(page, warnings);

        Page<Item> result = itemRepository.findAll(spec, PageRequest.of(pageNumber - 1, PAGE_SIZE, resolvedSort));

        List<ItemSearchItem> items = result.getContent().stream().map(ItemSearchItem::from).toList();
        String message = warnings.isEmpty() ? null : String.join("\n", warnings);
        log.debug("search returned {} of {} items", items.size(), result.getTotalElements());
        return new ItemSearchResponse(items, result.getTotalElements(), message);
    }

    /** 공개(status='A') 문항만 — 삭제 · 검수중은 항상 제외한다. */
    private static Specification<Item> statusActive() {
        return (root, query, cb) -> cb.equal(root.get("status"), ItemStatus.ACTIVE);
    }

    /** 키워드 — 제목 또는 지문에 포함(LIKE). 100자 초과분은 잘라 쓴다. */
    private Specification<Item> keywordFilter(String raw, List<String> warnings) {
        String q = raw == null ? "" : raw.trim();
        if (codePointLength(q) > KEYWORD_MAX_LENGTH) {
            q = truncateByCodePoints(q, KEYWORD_MAX_LENGTH);
            warnings.add("키워드가 너무 길어 100자까지만 사용했습니다.");
        }
        if (q.isEmpty()) {
            return null;
        }
        if (codePointLength(q) == 1) {
            warnings.add("키워드가 한 글자라 결과가 많을 수 있습니다.");
        }
        if (q.contains("%") || q.contains("_")) {
            warnings.add("키워드의 % 와 _ 는 와일드카드로 처리됩니다.");
        }
        String like = "%" + q + "%";
        return (root, query, cb) -> cb.or(cb.like(root.get("title"), like), cb.like(root.get("stem"), like));
    }

    /** 단원 — 코드와 정확히 일치. 형식이 이상하거나 등록돼 있지 않아도 경고만 남기고 그대로 조회한다(대개 0건). */
    private Specification<Item> unitFilter(String raw, List<String> warnings) {
        String unit = raw == null ? "" : raw.trim();
        if (unit.isEmpty()) {
            return null;
        }
        if (!UNIT_CODE_PATTERN.matcher(unit).matches()) {
            warnings.add("단원 코드 형식이 올바르지 않습니다. (예: M5-1)");
        }
        if (!unit.equals(unit.toUpperCase(Locale.ROOT))) {
            warnings.add("단원 코드는 대문자로 입력하세요. (입력값 그대로 조회합니다)");
        }
        if (unitRepository.findByCode(unit).isEmpty()) {
            warnings.add("등록되지 않은 단원 코드입니다: " + unit);
        }
        String matchUnit = unit;
        return (root, query, cb) -> cb.equal(root.get("unit").get("code"), matchUnit);
    }

    /** 난이도 — 빈 값이면 5 미만(1~4)만, 1~5 값이면 정확히 그 값, 그 밖의 값도 정수로 캐스팅해 비교한다(대개 0건). */
    private Specification<Item> levelFilter(String raw, List<String> warnings) {
        String level = raw == null ? "" : raw.trim();
        if (level.isEmpty()) {
            return (root, query, cb) -> cb.lessThan(root.get("level"), 5);
        }
        if (LEVEL_EXACT_PATTERN.matcher(level).matches()) {
            int value = Integer.parseInt(level);
            return (root, query, cb) -> cb.equal(root.get("level"), value);
        }
        warnings.add("난이도는 1~5 사이여야 합니다.");
        int value = phpStyleIntCast(level);
        return (root, query, cb) -> cb.equal(root.get("level"), value);
    }

    /** 태그 — 이름과 정확히 일치하는 문항만(부분 일치 없음). 등록돼 있지 않아도 경고만 남기고 그대로 조회한다(대개 0건). */
    private Specification<Item> tagFilter(String raw, List<String> warnings) {
        String tag = raw == null ? "" : raw.trim();
        if (tag.isEmpty()) {
            return null;
        }
        if (tag.contains("%") || tag.contains("_")) {
            warnings.add("태그는 부분 일치를 지원하지 않습니다.");
        }
        if (codePointLength(tag) > TAG_MAX_LENGTH) {
            tag = truncateByCodePoints(tag, TAG_MAX_LENGTH);
            warnings.add("태그 이름이 너무 길어 50자까지만 사용했습니다.");
        }
        if (tagRepository.findByName(tag).isEmpty()) {
            warnings.add("등록되지 않은 태그입니다: " + tag);
        }
        String matchTag = tag;
        return (root, query, cb) -> {
            Join<Item, Tag> tags = root.join("tags");
            return cb.equal(tags.get("name"), matchTag);
        };
    }

    /**
     * 정렬 — 기본은 난이도 내림차순·id 오름차순. {@code unit} 정렬만 2차 기준에 난이도 내림차순이 끼어든다(그대로 이관).
     * 방향 기본값은 id·title·unit 은 오름차순, level·created 는 내림차순.
     */
    private Sort resolveSort(String rawSort, String rawDir, List<String> warnings) {
        String sort = rawSort == null ? "" : rawSort.trim().toLowerCase(Locale.ROOT);
        String dir = rawDir == null ? "" : rawDir.trim().toLowerCase(Locale.ROOT);
        if (!dir.equals("asc") && !dir.equals("desc")) {
            if (!dir.isEmpty()) {
                warnings.add("정렬 방향은 asc 또는 desc 만 가능합니다.");
            }
            dir = "";
        }

        Sort.Direction ascByDefault = dir.equals("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort.Direction descByDefault = dir.equals("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort defaultSort = Sort.by(Sort.Direction.DESC, "level").and(Sort.by(Sort.Direction.ASC, "id"));

        return switch (sort) {
            case "id" -> Sort.by(ascByDefault, "id");
            case "title" -> Sort.by(ascByDefault, "title").and(Sort.by(Sort.Direction.ASC, "id"));
            case "unit" -> Sort.by(ascByDefault, "unit.code")
                .and(Sort.by(Sort.Direction.DESC, "level"))
                .and(Sort.by(Sort.Direction.ASC, "id"));
            case "level" -> Sort.by(descByDefault, "level").and(Sort.by(Sort.Direction.ASC, "id"));
            case "created" -> Sort.by(descByDefault, "createdAt").and(Sort.by(Sort.Direction.ASC, "id"));
            case "" -> defaultSort;
            default -> {
                warnings.add("알 수 없는 정렬 기준입니다. 기본 정렬을 사용합니다.");
                yield defaultSort;
            }
        };
    }

    /** 페이지 — 한 페이지 20건, 1~999 로 클램프. 숫자가 아니면(빈 값·"1" 제외) 경고를 남기고 1페이지로. */
    private int resolvePage(String raw, List<String> warnings) {
        String pageRaw = raw == null ? "1" : raw;
        int page = 1;
        if (PAGE_DIGITS_PATTERN.matcher(pageRaw).matches()) {
            page = parseAsPageNumber(pageRaw);
        } else if (!pageRaw.isEmpty() && !pageRaw.equals("1")) {
            warnings.add("페이지 번호가 올바르지 않아 1페이지를 표시합니다.");
        }
        if (page < 1) {
            page = 1;
        }
        if (page > MAX_PAGE) {
            page = MAX_PAGE;
            warnings.add("페이지 번호는 999 를 넘을 수 없습니다.");
        }
        return page;
    }

    /** 숫자만 있는 문자열이지만 int 범위를 넘을 만큼 길 수 있어(레거시는 이런 값도 막지 않는다) 안전하게 큰 값으로 처리한다. */
    private static int parseAsPageNumber(String digits) {
        try {
            long value = Long.parseLong(digits);
            return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    /** PHP {@code (int)} 캐스트를 흉내 낸다: 선행 부호+숫자만 취하고, 없으면 0. */
    private static int phpStyleIntCast(String raw) {
        Matcher matcher = LEADING_INT_PATTERN.matcher(raw);
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group());
        } catch (NumberFormatException e) {
            return matcher.group().startsWith("-") ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        }
    }

    private static int codePointLength(String s) {
        return s.codePointCount(0, s.length());
    }

    private static String truncateByCodePoints(String s, int maxCodePoints) {
        int cut = s.offsetByCodePoints(0, maxCodePoints);
        return s.substring(0, cut);
    }
}
