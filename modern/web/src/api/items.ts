import { getJson } from './client';
import type { Item, ItemSearchParams, ItemSearchResult, Unit } from './types';

/** GET /api/units — 단원 목록 */
export function fetchUnits(signal?: AbortSignal): Promise<Unit[]> {
  return getJson<Unit[]>('/api/units', signal);
}

/** GET /api/units/{code}/items — 단원별 문항(status='A' 만 서버가 돌려준다) */
export function fetchUnitItems(code: string, signal?: AbortSignal): Promise<Item[]> {
  return getJson<Item[]>(`/api/units/${encodeURIComponent(code)}/items`, signal);
}

/** GET /api/items/{id} — 문항 상세(없으면 404) */
export function fetchItem(id: number, signal?: AbortSignal): Promise<Item> {
  return getJson<Item>(`/api/items/${id}`, signal);
}

/** GET /api/items/search — 문항 검색. 빈 값인 조건은 쿼리에서 뺀다. */
export function fetchItemSearch(
  params: ItemSearchParams,
  signal?: AbortSignal,
): Promise<ItemSearchResult> {
  const query = new URLSearchParams();
  for (const [name, value] of Object.entries(params)) {
    if (value !== '') {
      query.set(name, value);
    }
  }
  const qs = query.toString();
  return getJson<ItemSearchResult>(`/api/items/search${qs ? `?${qs}` : ''}`, signal);
}
