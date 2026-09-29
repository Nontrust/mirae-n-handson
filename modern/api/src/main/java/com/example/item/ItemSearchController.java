package com.example.item;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 문항 검색 API. legacy {@code search.php} 를 옮긴 것 — 파라미터 이름은 그대로 유지한다.
 * 형식이 안 맞거나 존재하지 않는 값도 400/404 없이 0건 응답으로 처리한다(경고는 서비스가 {@code message} 에 담는다).
 */
@RestController
@RequestMapping("/api/items")
public class ItemSearchController {

    private final ItemSearchService itemSearchService;

    public ItemSearchController(ItemSearchService itemSearchService) {
        this.itemSearchService = itemSearchService;
    }

    /** {@code GET /api/items/search} */
    @GetMapping("/search")
    public ItemSearchResponse search(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String unit,
        @RequestParam(required = false) String level,
        @RequestParam(required = false) String tag,
        @RequestParam(required = false) String sort,
        @RequestParam(required = false) String dir,
        @RequestParam(required = false) String page) {
        return itemSearchService.search(q, unit, level, tag, sort, dir, page);
    }
}
