import { useState } from 'react';
import type { FormEvent } from 'react';

import type { ItemSearchParams } from '../api/types';
import { useItemSearch } from '../hooks/useItemSearch';
import { AsyncSection } from './AsyncSection';
import { ItemSearchResults } from './ItemSearchResults';

const EMPTY: ItemSearchParams = { q: '', unit: '', level: '', tag: '', sort: '', dir: '', page: '' };

/** 문항 검색 화면(legacy search.php 이관). 조건 입력 → 제출 → 결과 표. */
export function ItemSearch() {
  const [draft, setDraft] = useState<ItemSearchParams>(EMPTY);
  const [submitted, setSubmitted] = useState<ItemSearchParams | null>(null);

  const result = useItemSearch(submitted);

  const handleChange = (name: keyof ItemSearchParams, value: string) => {
    setDraft((prev) => ({ ...prev, [name]: value }));
  };

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    setSubmitted({ ...draft });
  };

  return (
    <section className="browser__pane browser__pane--wide" aria-labelledby="search-heading">
      <h2 id="search-heading">문항 검색</h2>
      <form onSubmit={handleSubmit} aria-label="문항 검색">
        <label>
          키워드
          <input value={draft.q} onChange={(e) => handleChange('q', e.target.value)} />
        </label>
        <label>
          단원
          <input value={draft.unit} onChange={(e) => handleChange('unit', e.target.value)} />
        </label>
        <label>
          난이도
          <input value={draft.level} onChange={(e) => handleChange('level', e.target.value)} />
        </label>
        <label>
          태그
          <input value={draft.tag} onChange={(e) => handleChange('tag', e.target.value)} />
        </label>
        <label>
          정렬
          <select value={draft.sort} onChange={(e) => handleChange('sort', e.target.value)}>
            <option value="">기본</option>
            <option value="id">번호</option>
            <option value="title">제목</option>
            <option value="unit">단원</option>
            <option value="level">난이도</option>
            <option value="created">등록일</option>
          </select>
        </label>
        <label>
          방향
          <select value={draft.dir} onChange={(e) => handleChange('dir', e.target.value)}>
            <option value="">기본</option>
            <option value="asc">오름차순</option>
            <option value="desc">내림차순</option>
          </select>
        </label>
        <label>
          페이지
          <input value={draft.page} onChange={(e) => handleChange('page', e.target.value)} />
        </label>
        <button type="submit">검색</button>
      </form>
      <AsyncSection state={result} idleMessage="조건을 입력하고 검색하세요">
        {(data) => <ItemSearchResults result={data} />}
      </AsyncSection>
    </section>
  );
}
