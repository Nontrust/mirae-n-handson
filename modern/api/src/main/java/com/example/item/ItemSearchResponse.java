package com.example.item;

import java.util.List;

/**
 * {@code GET /api/items/search} 응답. characterization 정규화 결과 모양
 * ({@code {status, rows, count, message}})의 {@code rows} 에 해당하는 필드 이름을 {@code items} 로 둔다
 * ({@code normalizeJson} 이 {@code items} 배열을 {@code rows} 로 읽는다).
 */
public record ItemSearchResponse(List<ItemSearchItem> items, long count, String message) {
}
