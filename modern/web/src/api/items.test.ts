import { mockFetch } from '../test/mockFetch';
import { fetchItemSearch } from './items';
import type { ItemSearchParams } from './types';

const EMPTY: ItemSearchParams = { q: '', unit: '', level: '', tag: '', sort: '', dir: '', page: '' };

describe('fetchItemSearch 쿼리 문자열 조립', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  const body = { items: [], count: 0, message: null };

  it('모든 조건이 비어 있으면 쿼리 문자열 없이 호출한다', async () => {
    const spy = mockFetch({ '/api/items/search': { body } });
    await fetchItemSearch(EMPTY);
    expect(String(spy.mock.calls[0]?.[0])).toBe('http://localhost:8080/api/items/search');
  });

  it('빈 값인 조건은 빼고 값이 있는 조건만 보낸다', async () => {
    const spy = mockFetch({ '/api/items/search': { body } });
    await fetchItemSearch({ ...EMPTY, q: '분수', level: '3' });
    const url = new URL(String(spy.mock.calls[0]?.[0]));
    expect(url.pathname).toBe('/api/items/search');
    expect([...url.searchParams.keys()].sort()).toEqual(['level', 'q']);
    expect(url.searchParams.get('q')).toBe('분수');
    expect(url.searchParams.get('level')).toBe('3');
  });

  it('특수문자와 한글은 인코딩되어 원래 값으로 복원된다', async () => {
    const spy = mockFetch({ '/api/items/search': { body } });
    await fetchItemSearch({ ...EMPTY, q: 'a&b=c 한글', tag: '오답률높음' });
    const raw = String(spy.mock.calls[0]?.[0]);
    expect(raw).not.toContain('a&b=c');
    const url = new URL(raw);
    expect(url.searchParams.get('q')).toBe('a&b=c 한글');
    expect(url.searchParams.get('tag')).toBe('오답률높음');
  });

  it('공백만 있는 값은 그대로 보낸다(서버가 검증)', async () => {
    const spy = mockFetch({ '/api/items/search': { body } });
    await fetchItemSearch({ ...EMPTY, q: ' ' });
    expect(new URL(String(spy.mock.calls[0]?.[0])).searchParams.get('q')).toBe(' ');
  });
});
