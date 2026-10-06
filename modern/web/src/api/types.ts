/**
 * modern/api(Spring, 8080) 응답 모양 — com.example.item.UnitResponse · ItemResponse 와 1:1.
 * 서버 DTO가 바뀌면 이 파일과 src/api/items.ts 만 고친다.
 */

/** 난이도 1~5 */
export type Level = 1 | 2 | 3 | 4 | 5;

/** A=공개 D=삭제 R=검수중 */
export type ItemStatus = 'A' | 'D' | 'R';

/** GET /api/units 의 행 */
export interface Unit {
  id: number;
  code: string;
  name: string;
  grade: number;
}

/**
 * GET /api/units/{code}/items 의 행이자 GET /api/items/{id} 의 응답.
 * unit 은 단원 코드(M5-1), tags 는 태그 이름 목록(서버 순서 유지).
 */
export interface Item {
  id: number;
  title: string;
  stem: string;
  unit: string;
  level: Level;
  status: ItemStatus;
  tags: string[];
}

/** GET /api/items/search 의 행 — com.example.item.ItemSearchItem 과 1:1 (stem · status 없음). */
export interface ItemSearchRow {
  id: number;
  title: string;
  unit: string;
  level: Level;
  tags: string[];
}

/** GET /api/items/search 의 응답. message 는 검증 경고 또는 0건 안내(없으면 null). */
export interface ItemSearchResult {
  items: ItemSearchRow[];
  count: number;
  message: string | null;
}

/** 검색 조건. 서버가 레거시식으로 검증하므로 값은 모두 문자열 그대로 보낸다. */
export interface ItemSearchParams {
  q: string;
  unit: string;
  level: string;
  tag: string;
  sort: string;
  dir: string;
  page: string;
}
