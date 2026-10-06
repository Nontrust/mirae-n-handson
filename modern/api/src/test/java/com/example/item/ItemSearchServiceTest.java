package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 저장소 슬라이스 — H2 에 실제로 질의한다. legacy search.php(BUSINESS-RULES.md 의 BR-xx)를
 * 옮긴 {@link ItemSearchService} 가 규칙대로 동작하는지 확인한다.
 *
 * 대소문자 무시 여부(BR-12)처럼 DB collation 에 좌우되는 동작은 H2 와 실제 MariaDB 가 다를 수 있어
 * 여기서 단정하지 않는다 — 최종 확인은 characterization 의 동작 보존 테스트(npm test)에서 한다.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ItemSearchService.class)
@ActiveProfiles("test")
class ItemSearchServiceTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ItemSearchService itemSearchService;

    private Item level1M5;
    private Item level4M5;
    private Item level5M5;
    private Item level2M6;

    @BeforeEach
    void seed() {
        Unit m5u1 = entityManager.persist(ItemFixtures.unit(1, "M5-1", "분수의 덧셈과 뺄셈", 5));
        Unit m6u1 = entityManager.persist(ItemFixtures.unit(2, "M6-1", "분수의 나눗셈", 6));
        Tag calc = entityManager.persist(ItemFixtures.tag(1, "계산"));
        Tag word = entityManager.persist(ItemFixtures.tag(2, "문장제"));

        level1M5 = entityManager.persist(ItemFixtures.item(null, m5u1, "분모가 같은 분수의 덧셈", 1, ItemStatus.ACTIVE, calc));
        level4M5 = entityManager.persist(ItemFixtures.item(null, m5u1, "분수 문장제", 4, ItemStatus.ACTIVE, calc, word));
        entityManager.persist(ItemFixtures.item(null, m5u1, "검수 중 문항", 3, ItemStatus.REVIEWING));
        entityManager.persist(ItemFixtures.item(null, m5u1, "삭제된 분수 문항", 5, ItemStatus.DELETED));
        level5M5 = entityManager.persist(ItemFixtures.item(null, m5u1, "난이도 5 분수", 5, ItemStatus.ACTIVE, word));
        level2M6 = entityManager.persist(ItemFixtures.item(null, m6u1, "소수 곱셈", 2, ItemStatus.ACTIVE));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("BR-09/BR-24: 키워드는 제목·지문에서 찾고, 공개(A) 문항만 나온다")
    void keywordMatchesTitleAndOnlyActiveStatus() {
        ItemSearchResponse result = itemSearchService.search("분수", null, null, null, null, null, null);

        assertThat(result.items()).extracting(ItemSearchItem::id)
            .containsExactly(level4M5.getId(), level1M5.getId());
        assertThat(result.message()).isNull();
    }

    @Test
    @DisplayName("BR-10: 100자 넘는 키워드는 잘라 쓰고 경고를 남긴다")
    void longKeywordIsTruncatedAndWarns() {
        String tooLong = "가".repeat(101);

        ItemSearchResponse result = itemSearchService.search(tooLong, null, null, null, null, null, null);

        assertThat(result.count()).isZero();
        assertThat(result.message()).isEqualTo("키워드가 너무 길어 100자까지만 사용했습니다.\n검색 결과가 없습니다");
    }

    @Test
    @DisplayName("BR-13: 난이도를 지정하지 않으면 5는 빠진다")
    void emptyLevelExcludesFive() {
        ItemSearchResponse result = itemSearchService.search(null, "M5-1", null, null, null, null, null);

        assertThat(result.items()).extracting(ItemSearchItem::id)
            .containsExactly(level4M5.getId(), level1M5.getId());
    }

    @Test
    @DisplayName("BR-14: 난이도를 5로 지정하면 빈 값일 때 빠지던 5가 포함된다")
    void explicitLevelFiveIncludesIt() {
        ItemSearchResponse result = itemSearchService.search(null, "M5-1", "5", null, null, null, null);

        assertThat(result.items()).extracting(ItemSearchItem::id).containsExactly(level5M5.getId());
        assertThat(result.message()).isNull();
    }

    @Test
    @DisplayName("BR-15: 범위 밖 난이도는 경고만 남기고 정수 비교로 그대로 진행한다(대개 0건)")
    void outOfRangeLevelWarnsAndStillQueries() {
        ItemSearchResponse result = itemSearchService.search(null, null, "10", null, null, null, null);

        assertThat(result.count()).isZero();
        assertThat(result.message()).isEqualTo("난이도는 1~5 사이여야 합니다.\n검색 결과가 없습니다");
    }

    @Test
    @DisplayName("BR-16: 태그는 이름이 정확히 일치하는 문항만 찾는다")
    void tagMatchesExactly() {
        ItemSearchResponse result = itemSearchService.search(null, null, null, "계산", null, null, null);

        assertThat(result.items()).extracting(ItemSearchItem::id)
            .containsExactly(level4M5.getId(), level1M5.getId());
    }

    @Test
    @DisplayName("BR-17: 등록되지 않은 태그는 경고만 남기고 조회는 그대로 진행한다(0건)")
    void unknownTagWarnsAndReturnsEmpty() {
        ItemSearchResponse result = itemSearchService.search(null, null, null, "없는태그", null, null, null);

        assertThat(result.count()).isZero();
        assertThat(result.message()).isEqualTo("등록되지 않은 태그입니다: 없는태그\n검색 결과가 없습니다");
    }

    @Test
    @DisplayName("BR-26: 단원과 난이도를 함께 지정하면 AND 로 결합된다")
    void unitAndLevelCombineWithAnd() {
        ItemSearchResponse result = itemSearchService.search(null, "M6-1", "2", null, null, null, null);

        assertThat(result.items()).extracting(ItemSearchItem::id).containsExactly(level2M6.getId());
    }

    @Test
    @DisplayName("BR-18: 정렬을 지정하지 않으면 난이도 내림차순, 같은 난이도면 id 오름차순")
    void defaultSortIsLevelDescIdAsc() {
        ItemSearchResponse result = itemSearchService.search(null, null, null, null, null, null, null);

        assertThat(result.items()).extracting(ItemSearchItem::id)
            .containsExactly(level4M5.getId(), level2M6.getId(), level1M5.getId());
    }

    @Test
    @DisplayName("BR-19: sort=unit 일 때만 2차 정렬에 난이도 내림차순이 끼어든다")
    void unitSortHasLevelDescAsSecondaryOrder() {
        ItemSearchResponse result = itemSearchService.search(null, null, null, null, "unit", null, null);

        assertThat(result.items()).extracting(ItemSearchItem::id)
            .containsExactly(level4M5.getId(), level1M5.getId(), level2M6.getId());
    }

    @Test
    @DisplayName("BR-18: 알 수 없는 정렬 기준은 경고를 남기고 기본 정렬로 돌아간다")
    void unknownSortFallsBackToDefaultWithWarning() {
        ItemSearchResponse result = itemSearchService.search(null, null, null, null, "banana", null, null);

        assertThat(result.items()).extracting(ItemSearchItem::id)
            .containsExactly(level4M5.getId(), level2M6.getId(), level1M5.getId());
        assertThat(result.message()).isEqualTo("알 수 없는 정렬 기준입니다. 기본 정렬을 사용합니다.");
    }

    @Test
    @DisplayName("BR-23: 페이지가 999 를 넘으면 999 로 고정하고 경고를 남긴다")
    void pageAbove999IsClampedWithWarning() {
        ItemSearchResponse result = itemSearchService.search(null, null, null, null, null, null, "1000");

        assertThat(result.count()).isEqualTo(3);
        assertThat(result.items()).isEmpty();
        assertThat(result.message()).isEqualTo("페이지 번호는 999 를 넘을 수 없습니다.");
    }

    @Test
    @DisplayName("단원 코드가 없으면(형식은 맞지만 미등록) 경고만 남기고 0건")
    void unknownUnitCodeWarnsAndReturnsEmpty() {
        ItemSearchResponse result = itemSearchService.search(null, "M9-9", null, null, null, null, null);

        assertThat(result.count()).isZero();
        assertThat(result.message()).isEqualTo("등록되지 않은 단원 코드입니다: M9-9\n검색 결과가 없습니다");
    }

    @Test
    @DisplayName("문항 행에는 id·title·unit·level·tags 만 담긴다(태그는 등록 순서)")
    void rowOnlyHasNormalizedFields() {
        ItemSearchResponse result = itemSearchService.search(null, "M5-1", "4", null, null, null, null);

        assertThat(result.items()).hasSize(1);
        ItemSearchItem row = result.items().get(0);
        assertThat(row.id()).isEqualTo(level4M5.getId());
        assertThat(row.title()).isEqualTo("분수 문장제");
        assertThat(row.unit()).isEqualTo("M5-1");
        assertThat(row.level()).isEqualTo(4);
        assertThat(row.tags()).containsExactly("계산", "문장제");
    }

    @Test
    @DisplayName("BR-24: 결과가 0건이면 경고가 없어도 '검색 결과가 없습니다'를 message 에 담는다")
    void emptyResultAddsNoResultMessage() {
        ItemSearchResponse result = itemSearchService.search("존재하지않는키워드", null, null, null, null, null, null);

        assertThat(result.count()).isZero();
        assertThat(result.message()).isEqualTo("검색 결과가 없습니다");
    }
}
