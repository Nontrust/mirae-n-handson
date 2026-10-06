import { renderHook, waitFor } from '@testing-library/react';

import type { ItemSearchParams, ItemSearchResult } from '../api/types';
import { mockFetch } from '../test/mockFetch';
import { useItemSearch } from './useItemSearch';

const params: ItemSearchParams = { q: '분수', unit: '', level: '', tag: '', sort: '', dir: '', page: '' };
const result: ItemSearchResult = {
  items: [{ id: 2, title: '분모가 다른 분수의 뺄셈', unit: 'M5-1', level: 3, tags: ['계산'] }],
  count: 1,
  message: null,
};

describe('useItemSearch (fetch mock)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('params 가 null 이면 조회하지 않고 idle 상태를 유지한다', () => {
    const spy = mockFetch({});
    const { result: r } = renderHook(() => useItemSearch(null));
    expect(r.current.status).toBe('idle');
    expect(spy).not.toHaveBeenCalled();
  });

  it('loading → success 로 바뀌고 검색 조건이 쿼리로 전달된다', async () => {
    const spy = mockFetch({ '/api/items/search': { body: result } });
    const { result: r } = renderHook(() => useItemSearch(params));
    expect(r.current.status).toBe('loading');
    await waitFor(() => expect(r.current.status).toBe('success'));
    expect(r.current.data).toEqual(result);
    expect(spy).toHaveBeenCalledTimes(1);
    expect(String(spy.mock.calls[0]?.[0])).toBe('http://localhost:8080/api/items/search?q=%EB%B6%84%EC%88%98');
  });

  it('5xx 응답이면 error 상태와 서버 오류 메시지를 돌려준다', async () => {
    mockFetch({ '/api/items/search': { status: 500, body: { message: 'boom' } } });
    const { result: r } = renderHook(() => useItemSearch(params));
    await waitFor(() => expect(r.current.status).toBe('error'));
    expect(r.current.error).toBe('서버 오류 (500)');
  });
});
