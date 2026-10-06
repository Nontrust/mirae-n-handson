import type { ItemSearchResult } from '../api/types';
import { LevelBadge } from './LevelBadge';

interface ItemSearchResultsProps {
  result: ItemSearchResult;
}

/** 검색 결과 표. 서버가 준 안내 문구(message)와 건수를 그대로 보여준다. */
export function ItemSearchResults({ result }: ItemSearchResultsProps) {
  return (
    <div>
      {result.message ? (
        <p className="hint" role="status" style={{ whiteSpace: 'pre-line' }}>
          {result.message}
        </p>
      ) : null}
      {result.items.length > 0 ? (
        <table className="item-table">
          <caption className="sr-only">검색 결과 ({result.count}건)</caption>
          <thead>
            <tr>
              <th scope="col">번호</th>
              <th scope="col">제목</th>
              <th scope="col">단원</th>
              <th scope="col">난이도</th>
              <th scope="col">태그</th>
            </tr>
          </thead>
          <tbody>
            {result.items.map((item) => (
              <tr key={item.id}>
                <td>{item.id}</td>
                <td>{item.title}</td>
                <td>{item.unit}</td>
                <td>
                  <LevelBadge level={item.level} />
                </td>
                <td>{item.tags.length > 0 ? item.tags.join(', ') : '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}
      <p className="hint">총 {result.count}건</p>
    </div>
  );
}
