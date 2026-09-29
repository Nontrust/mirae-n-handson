package com.example.item;

import java.util.List;

/**
 * 문항 검색 결과 한 행. {@code characterization/lib/normalize.mjs} 가 만드는 정규화 결과의
 * {@code rows} 항목과 필드 이름 · 개수를 맞춘다(id, title, unit, level, tags) — stem · status 는 넣지 않는다.
 */
public record ItemSearchItem(Integer id, String title, String unit, Integer level, List<String> tags) {

    static ItemSearchItem from(Item item) {
        return new ItemSearchItem(
            item.getId(),
            item.getTitle(),
            item.getUnit().getCode(),
            item.getLevel(),
            item.getTags().stream().map(Tag::getName).toList());
    }
}
