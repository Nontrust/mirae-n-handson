// 동작 보존 테스트 — 문항 은행(item-bank)의 문항 검색 화면 search.php
//
// 대상: GET /search.php (buildSearchQuery + runSearchQuery, 조회만 함 — 데이터를 바꾸지 않음)
// 근거 문서: docs/item-bank/BUSINESS-RULES.md, docs/item-bank/ARCHITECTURE.md
//
// 케이스 구성(README.md 케이스 작성 규칙): 정상 3 · 경계값 5 · 빈값·누락 2 · 이상한 값 3 = 13개.
// 이름에 겨냥한 규칙 ID(BR-xx, docs/item-bank/BUSINESS-RULES.md)를 남겨 스냅샷이 왜 그 모양인지 추적할 수 있게 한다.
//
// message에는 search.php의 <ul id="warnings">(형식 오류·범위 밖 값·페이지 클램프 등의 경고)와
// <p id="message">(0건 안내)가 순서대로 줄바꿈으로 합쳐져 들어간다(lib/normalize.mjs). 100자 키워드 자르기
// (BR-10)처럼 seed 데이터로는 rows 차이로 드러나지 않는 규칙도 경고 문구로 관찰된다.
import { describe, expect, it } from 'vitest';
import { fetchNormalized } from '../lib/target.mjs';

const MODULE = 'item-bank';

describe('item-bank · 문항 검색(search.php)', () => {
  it('정상 — 키워드 검색: q=분수 (BR-09, BR-24)', async () => {
    const result = await fetchNormalized(MODULE, '/search.php', { q: '분수' });
    expect(result).toMatchSnapshot();
  });

  it('정상 — 단원과 난이도 조합: unit=M5-1&level=3 (BR-11, BR-14, BR-26)', async () => {
    const result = await fetchNormalized(MODULE, '/search.php', { unit: 'M5-1', level: 3 });
    expect(result).toMatchSnapshot();
  });

  it('정상 — 태그와 정렬 조합: tag=오답률높음&sort=level (BR-16, BR-20)', async () => {
    const result = await fetchNormalized(MODULE, '/search.php', { tag: '오답률높음', sort: 'level' });
    expect(result).toMatchSnapshot();
  });

  it('경계값 — 난이도 하한: level=1 (BR-14)', async () => {
    const result = await fetchNormalized(MODULE, '/search.php', { level: 1 });
    expect(result).toMatchSnapshot();
  });

  it('경계값 — 난이도 상한 명시(빈 값일 때의 제외와 대조): level=5 (BR-14, BR-13)', async () => {
    const result = await fetchNormalized(MODULE, '/search.php', { level: 5 });
    expect(result).toMatchSnapshot();
  });

  it('경계값 — 키워드 100자(자르기 임계값 바로 아래): q 100자 (BR-10)', async () => {
    const q = '분수'.repeat(50); // 정확히 100자
    const result = await fetchNormalized(MODULE, '/search.php', { q });
    expect(result).toMatchSnapshot();
  });

  it('경계값 — 키워드 101자(자르기 임계값을 1자 넘김): q 101자 (BR-10)', async () => {
    const q = `${'분수'.repeat(50)}분`; // 정확히 101자
    const result = await fetchNormalized(MODULE, '/search.php', { q });
    expect(result).toMatchSnapshot();
  });

  it('경계값 — 페이지 상한: page=999 (BR-23)', async () => {
    const result = await fetchNormalized(MODULE, '/search.php', { page: 999 });
    expect(result).toMatchSnapshot();
  });

  it('빈값·누락 — 파라미터 전체 누락: 기본값 조합 (BR-13, BR-18, BR-23)', async () => {
    const result = await fetchNormalized(MODULE, '/search.php');
    expect(result).toMatchSnapshot();
  });

  it('빈값·누락 — 단원 파라미터 빈 문자열: unit= (BR-11)', async () => {
    const result = await fetchNormalized(MODULE, '/search.php', { unit: '' });
    expect(result).toMatchSnapshot();
  });

  it('이상한 값 — 음수 페이지: page=-5 (BR-23)', async () => {
    const result = await fetchNormalized(MODULE, '/search.php', { page: -5 });
    expect(result).toMatchSnapshot();
  });

  it('이상한 값 — 아주 긴 키워드(500자): q 500자 (BR-10)', async () => {
    const q = '분수'.repeat(250); // 정확히 500자
    const result = await fetchNormalized(MODULE, '/search.php', { q });
    expect(result).toMatchSnapshot();
  });

  it('이상한 값 — 존재하지 않는 단원 코드: unit=M7-1 (BR-11)', async () => {
    const result = await fetchNormalized(MODULE, '/search.php', { unit: 'M7-1' });
    expect(result).toMatchSnapshot();
  });
});
