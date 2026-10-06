import { fetchItemSearch } from '../api/items';
import type { ItemSearchParams, ItemSearchResult } from '../api/types';
import { useApiQuery } from './useApiQuery';
import type { QueryState } from './useApiQuery';

/** 문항 검색. params 가 null 이면(검색 전) 조회하지 않는다. */
export function useItemSearch(params: ItemSearchParams | null): QueryState<ItemSearchResult> {
  return useApiQuery(params === null ? null : `items/search?${JSON.stringify(params)}`, (signal) =>
    fetchItemSearch(params as ItemSearchParams, signal),
  );
}
