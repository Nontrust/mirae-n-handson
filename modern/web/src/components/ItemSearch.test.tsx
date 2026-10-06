import { fireEvent, render, screen } from '@testing-library/react';

import { mockFetch, mockFetchNetworkError } from '../test/mockFetch';
import { ItemSearch } from './ItemSearch';

describe('ItemSearch (fetch mock)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('제출 전에는 안내 문구만 보이고 API 를 호출하지 않는다', () => {
    const spy = mockFetch({});
    render(<ItemSearch />);
    expect(screen.getByText('조건을 입력하고 검색하세요')).toBeTruthy();
    expect(spy).not.toHaveBeenCalled();
  });

  it('조건을 입력하고 검색하면 쿼리로 호출하고 결과 표를 그린다', async () => {
    const spy = mockFetch({
      '/api/items/search': {
        body: {
          items: [{ id: 2, title: '분모가 다른 분수의 뺄셈', unit: 'M5-1', level: 3, tags: ['계산'] }],
          count: 1,
          message: null,
        },
      },
    });
    render(<ItemSearch />);
    fireEvent.change(screen.getByLabelText('키워드'), { target: { value: '분수' } });
    fireEvent.change(screen.getByLabelText('정렬'), { target: { value: 'level' } });
    fireEvent.click(screen.getByRole('button', { name: '검색' }));

    expect(await screen.findByText('분모가 다른 분수의 뺄셈')).toBeTruthy();
    expect(screen.getByText('총 1건')).toBeTruthy();
    const url = new URL(String(spy.mock.calls[0]?.[0]));
    expect(url.pathname).toBe('/api/items/search');
    expect(url.searchParams.get('q')).toBe('분수');
    expect(url.searchParams.get('sort')).toBe('level');
    expect(url.searchParams.has('unit')).toBe(false);
  });

  it('API 서버에 연결할 수 없으면 오류 문구를 보여준다', async () => {
    mockFetchNetworkError();
    render(<ItemSearch />);
    fireEvent.click(screen.getByRole('button', { name: '검색' }));
    expect((await screen.findByRole('alert')).textContent).toBe('API 서버에 연결할 수 없습니다');
  });
});
