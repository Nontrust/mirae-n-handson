import { render, screen } from '@testing-library/react';

import type { ItemSearchResult } from '../api/types';
import { ItemSearchResults } from './ItemSearchResults';

describe('ItemSearchResults', () => {
  it('결과 행의 번호 · 제목 · 단원 · 난이도 · 태그를 표로 보여준다', () => {
    const result: ItemSearchResult = {
      items: [
        { id: 2, title: '분모가 다른 분수의 뺄셈', unit: 'M5-1', level: 3, tags: ['계산', '오답률높음'] },
        { id: 3, title: '분수 덧셈 문장제', unit: 'M5-1', level: 5, tags: [] },
      ],
      count: 2,
      message: null,
    };
    render(<ItemSearchResults result={result} />);
    expect(screen.getByText('분모가 다른 분수의 뺄셈')).toBeTruthy();
    expect(screen.getByText('계산, 오답률높음')).toBeTruthy();
    expect(screen.getByLabelText('난이도 5 (매우 어려움)')).toBeTruthy();
    expect(screen.getByText('—')).toBeTruthy();
    expect(screen.getByText('총 2건')).toBeTruthy();
    expect(screen.queryByRole('status')).toBeNull();
  });

  it('0건이면 표 없이 서버 안내 문구와 총 0건을 보여준다', () => {
    render(<ItemSearchResults result={{ items: [], count: 0, message: '검색 결과가 없습니다' }} />);
    expect(screen.queryByRole('table')).toBeNull();
    expect(screen.getByRole('status').textContent).toBe('검색 결과가 없습니다');
    expect(screen.getByText('총 0건')).toBeTruthy();
  });

  it('검증 경고 message 는 결과와 함께 표시된다', () => {
    render(
      <ItemSearchResults
        result={{
          items: [{ id: 1, title: '문항', unit: 'M5-1', level: 1, tags: [] }],
          count: 1,
          message: '난이도는 1~5 입니다',
        }}
      />,
    );
    expect(screen.getByRole('status').textContent).toBe('난이도는 1~5 입니다');
    expect(screen.getByRole('table')).toBeTruthy();
  });
});
