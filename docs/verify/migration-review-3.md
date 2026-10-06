판정: 반려 (승인 보류 — 기준 1 에 "확인 필요" 1건이 남음. 치명 이슈 0건, 미충족 기준 없음)

- 검증 대상: `upstream/main...HEAD -- modern characterization` + 아직 커밋되지 않은 `modern/web` 작업 트리 변경
- 요청 범위: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관.
- 사람이 확정한 범위: ① `GET /api/units` 의 `itemCount`(legacy `units.php` 의 `item_count` 와 맞춤)는 이번 이관에 포함. ② `modern/web` 검색 화면 이관도 포함.
- 이관된 엔드포인트: `GET /api/items/search` (legacy `search.php`)
- 수행: reviewer Sub-agent(체크리스트 대조), tester Sub-agent(테스트 추가 · 실행). 최종 판정은 메인 세션.
- 승인하지 못한 이유: 체크리스트는 "확인 필요"가 남은 기준이 있으면 승인하지 않고 사람에게 확인을 요청하도록 한다. 새 API 대상 characterization 이 실행되지 않았다.

## 기준별 결과

| 기준 | 결과 | 근거 |
|---|---|---|
| 1. 테스트 통과 | **확인 필요** | 실행한 항목은 모두 통과(실패 0). 그러나 이관의 동작 보존 최종 확인인 새 API 대상 `TARGET_BASE_URL=http://localhost:8080 npm test` 를 실행하지 않음(서버를 띄울 수 없었음). `modern/api` 결과는 `UP-TO-DATE` 캐시 집계라 새로 실행한 결과가 아님. |
| 2. 치명 이슈 0건 | 충족 | 비밀값 리터럴 없음, 데이터 삭제 · 덮어쓰기 없음(조회 전용), 인증 우회 경로 없음, SQL 은 Specification · `@Query` 파라미터 바인딩. |
| 3. 요청 범위 이탈 없음 | 충족 (확인 필요 사항은 범위 밖 파일에 한함) | 요청 문장 확인됨. `itemCount` · web 검색 화면은 사람이 범위에 포함시킴. `normalize.mjs` · README 변경은 베이스라인 커밋 4320bab 에 있고 `search.php` 의 경고 출력(`<ul id="warnings">`)을 `message` 로 정규화하기 위한 것임. 의존성 · `application.yml` · 스키마 변경 없음. |
| 4. 컨벤션 준수 | 충족 | 컨트롤러는 서비스만 주입, 다른 도메인 Repository 주입 없음, 빈 catch 없음(catch 2곳 모두 debug 로그 후 값 보정), `System.out` · `printStackTrace` · `console.log` 없음. |

## 테스트 실행 결과 (tester 보고)

| 모듈 | 명령 | 결과 |
|---|---|---|
| modern/web | `npm test` | 9개 파일 23 통과 · 0 실패 (기존 9 + 신규 14) |
| modern/web | `npm run typecheck` · `npm run lint` | 오류 없음 |
| modern/api | `./gradlew test` | BUILD SUCCESSFUL, 11개 클래스 41 통과 · 실패 0. **`UP-TO-DATE`** — 이번에 새로 실행한 것이 아니라 이전 실행 결과(`build/test-results` XML)를 집계함 |
| characterization (레거시 대상) | `npm test` | 3개 파일 27 통과 · 0 실패 |
| characterization (새 API 대상) | `TARGET_BASE_URL=http://localhost:8080 npm test` | **실행하지 않음** — 서버 기동 불가(서버 · docker 를 직접 건드리지 않도록 지시함) |

tester 가 추가한 테스트 파일(모두 새 파일, 기존 테스트 · fixtures · characterization 은 수정하지 않음): `modern/web/src/api/items.test.ts`(4), `hooks/useItemSearch.test.ts`(3), `components/ItemSearchResults.test.tsx`(3), `components/ItemSearch.test.tsx`(3).

## reviewer · tester 보고가 엇갈린 지점

- reviewer: "웹 신규 export(`fetchItemSearch`, `useItemSearch`, `ItemSearch`, `ItemSearchResults`)를 호출하는 테스트는 없음" — 경고 1번, 기준 1 "확인 필요".
- tester: 위 네 가지에 대한 테스트 14개를 새 파일로 추가했고 모두 통과.
- 메인 세션 판단: 두 에이전트를 동시에 시작해서 reviewer 가 테스트 추가 전 상태를 본 것으로 보인다(작업 트리에 위 4개 테스트 파일이 untracked 로 존재함을 `git status` 로 확인). 실질적인 충돌은 아니며 reviewer 의 경고 1번은 해소된 것으로 본다. 다만 테스트 파일은 아직 커밋되지 않았다.

## 이슈 목록

경고 (판정에 반영하지 않고 기록만)
1. 경고(중복 코드) · `modern/api/src/main/java/com/example/item/ItemSearchService.java:82, 102, 121, 136` · 근거: `raw == null ? "" : raw.trim()` 이 4곳에 복사돼 있음(162-163 은 `toLowerCase` 가 붙은 변형) · 수정 방향: `trimToEmpty(String)` 공통 메서드로 추출. (이관 중 동작 변경이 없도록 추출만 한다.)
2. 경고(참고) · `ItemSearchService.java` 의 `parseAsPageNumber` · 근거: page 를 문자열로 받아 서비스에서 레거시식으로 검증함. 레거시 동작 보존이며 검증 자체는 있음 · 수정 방향: 현 상태 유지 가능.

제안
3. 제안 · `modern/web/src/hooks/useItemSearch.ts:8` · `params as ItemSearchParams` 단언 대신 `params === null` 분기 안에서 좁히면 캐스팅을 없앨 수 있음.
4. 제안 · `modern/web/src/components/ItemSearch.tsx` · 단원 · 난이도 · 태그 · 페이지를 자유 텍스트로 받음. 서버가 레거시식 경고를 주는 설계에 맞춘 것이나 사용성은 별도 논의.
5. 제안 · `ItemSearchService` 의 `trim()` · `toUpperCase` · `phpStyleIntCast` 는 PHP 와 다를 수 있음(전각 공백, 비ASCII, `"1e3"` 이 PHP 는 1000, 여기서는 1). 해당 입력의 characterization 케이스를 추가해 확인.
6. 제안 · `title` 정렬은 MariaDB collation 기준인 레거시 결과와 비교가 필요함(`modern/api` 테스트는 H2 라 검증 불가).

## 확인 필요 (사람 확인)

1. **새 API 대상 characterization 실행**: 서버(`modern/api` bootRun, MariaDB 필요)를 HEAD 기준으로 띄운 뒤 `cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test` 를 실행해야 한다. CLAUDE.md 완료 기준상 이 통과 전에는 이관 완료로 보고할 수 없다.
2. **`modern/api` 테스트 재실행**: 이번 `./gradlew test` 는 캐시 결과이므로, 새로 실행한 결과가 필요하면 `./gradlew test --rerun-tasks` 를 실행한다.
3. **비교 범위 밖 작업 트리 변경**: `legacy/item-bank-php/docs/`(untracked, `legacy/` 하위 신규 파일), `.claude/agents/`, `.claude/settings.json`, `.claude/skills/verify/SKILL.md`(수정). 이번 이관과의 관련성 및 "`legacy/` 는 허락 없이 수정하지 않는다" 허락 여부를 확인해야 한다. reviewer 는 이 파일들의 내용을 검토하지 않았다.
4. **커밋 상태**: `modern/web` 검색 화면과 tester 가 추가한 테스트 파일이 아직 커밋되지 않았다. 커밋 전에는 `upstream/main...HEAD` 에 잡히지 않는다.

## 의심 동작 (레거시 동작이라 고치지 않고 그대로 이관됨)

- 난이도가 빈 값이면 `level < 5` 만 조회해 5단계 문항이 전체 검색에서 빠짐. 주석("1~5 모두 포함")과 코드가 모순됨 (`legacy/item-bank-php/search.php:214-221`).
- `sort=unit` 일 때만 2차 정렬이 난이도 내림차순임 (`search.php:295-297`).
- 키워드의 `%` · `_` 를 이스케이프하지 않아 와일드카드로 동작하고 경고만 표시함 (`search.php:75-77`).
- 범위 밖 난이도(`abc`, `99`)를 PHP `(int)` 캐스트로 변환해 조회하며 `abc` 는 0 이 되어 0건.
- 등록되지 않은 단원 · 태그도 오류 없이 경고 + 0건(404/400 아님). 숫자 아닌 page 는 경고만 내고 1페이지. 공백만 있는 page 등 일부 경계는 경고 없이 1페이지로 처리됨.
- 페이지가 총 페이지 수를 넘으면 빈 결과가 되고 클램프 상한이 999 로 고정임 (`search.php:339-348`).
- `tag` 검색이 태그 조인을 쓰므로, 한 문항에 같은 태그가 중복 매핑된 데이터가 있으면 중복 행 · 건수가 생길 수 있음(스키마 유니크 제약 확인 필요, 현 시드로는 미관찰).

## 참고

- reviewer 보고에 하네스가 "instruction-shaped pattern(settings-json)" 경고를 붙였다. `.claude/settings.json` 파일 이름을 언급한 데서 걸린 것으로 보이며, 보고 안에 따라야 할 지시는 없었다.
