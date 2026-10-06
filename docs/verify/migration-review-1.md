판정: 반려

- 검증 대상: `upstream/main...HEAD` (커밋 3개: 4320bab, df03263, 307b0e5)
- 요청 범위: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관.
- 이관된 엔드포인트: `GET /api/items/search` (legacy `search.php`)

## 기준별 결과

| 기준 | 결과 | 근거 |
|---|---|---|
| 1. 테스트 통과 | 미충족 | `modern/api` 40건 통과 · 0 실패 · 0 skip. characterization 레거시 대상 27건 통과. 새 API 대상(`TARGET_BASE_URL=http://localhost:8080`)은 item-bank 13건 중 **4건 실패**(22 통과 · 1 skip). 단위 테스트는 실패한 동작을 검증하지 않아 "테스트가 충분하다"도 미충족 |
| 2. 치명 이슈 0건 | 미충족 | 이슈 1번(동작 보존 실패)을 치명으로 분류. 비밀값 · SQL 문자열 결합 · 데이터 삭제 코드는 없음(검색은 Specification 바인딩 사용) |
| 3. 요청 범위 이탈 없음 | 미충족 | `/api/units` 응답 변경, `CLAUDE.md` · `.claude/skills/*` · `docs/*` 추가가 "엔드포인트 1개 이관"으로 설명되지 않음. 의존성 · 스키마 · 설정 파일 변경은 없음 |
| 4. 컨벤션 준수 | 충족(경고 1건) | 컨트롤러 → 서비스 → 리포지토리 계층, 생성자 주입, record DTO, SLF4J 사용. `System.out` 등 임시 출력 없음. 서비스가 `UnitRepository` · `TagRepository` 를 직접 주입(같은 `item` 도메인이라 허용). 빈 catch 는 없음 — 다만 이슈 5 참고 |

## 실행한 테스트

| 명령 | 결과 |
|---|---|
| `cd modern/api && ./gradlew test --rerun-tasks` | 40 통과 · 0 실패 · 0 skip |
| `cd characterization && npm test` (레거시 :8081) | 27 통과 · 0 실패 |
| `cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test` | item-bank 22 통과 · **4 실패** · 1 skip(example-units, 레거시 전용 `skipIf`) |

실패한 테스트(`characterization/tests/item-bank.test.js`):
- 경계값 — 키워드 100자: q 100자 (BR-10)
- 경계값 — 키워드 101자: q 101자 (BR-10)
- 이상한 값 — 아주 긴 키워드(500자): q 500자 (BR-10)
- 이상한 값 — 존재하지 않는 단원 코드: unit=M7-1 (BR-11)

차이(핵심):
- M7-1: 기대 `"등록되지 않은 단원 코드입니다: M7-1\n검색 결과가 없습니다"` / 실제 `"등록되지 않은 단원 코드입니다: M7-1"`
- 키워드 100자: 기대 `"검색 결과가 없습니다"` / 실제 `null`
- 키워드 101자 · 500자: 기대 `"키워드가 너무 길어 100자까지만 사용했습니다.\n검색 결과가 없습니다"` / 실제 앞 줄만

스냅샷 · characterization 테스트는 수정하지 않았다(검증 중 코드 · 스냅샷 변경 없음).

## 이슈 목록

1. **치명 · `modern/api/src/main/java/com/example/item/ItemSearchService.java:64-66` · 0건일 때 안내 문구가 빠짐 · 레거시는 결과가 0건이면 `<p id="message">검색 결과가 없습니다</p>` 를 출력하는데(`legacy/item-bank-php/search.php:658`) 새 API 의 `message` 는 경고만 담는다. 모든 0건 응답에서 동작이 달라져 "동작을 바꾸지 않고"라는 요청을 어긴다 · 수정 방향: 결과가 0건이면 경고 뒤에 `검색 결과가 없습니다` 를 줄바꿈으로 덧붙인다. 단위 테스트에 0건 케이스를 추가한다.**
2. **경고 · `modern/api/src/main/java/com/example/item/UnitService.java:26` · 이관 대상이 아닌 `GET /api/units` 응답에 `itemCount` 필드를 추가(`UnitResponse.java`)하고 단원마다 `count` 쿼리를 실행(단원 수만큼 쿼리, N+1) · 요청은 엔드포인트 1개 이관인데 기존 엔드포인트의 응답 모양을 바꿨다. 프런트 소비 여부는 확인하지 못했다 · 수정 방향: 이 변경이 요청에 포함돼 있었는지 확인하고, 아니면 분리한다. 필요하면 그룹 쿼리 한 번으로 센다.**
3. **경고 · `CLAUDE.md`, `.claude/skills/{convention-check,document-module,impact}/SKILL.md`, `docs/assignment/*`, `docs/item-bank/*` · 변경 파일 24개 중 14개(문서 · Skill · 가이드)가 이관 요청으로 설명되지 않음. `upstream/main` 이 옛 커밋(1bb4f2f)이라 diff 에 함께 잡힌 것일 수 있다 · 수정 방향: 이 PR 에 포함할 범위인지 확인하고, 아니면 분리한다.**
4. **경고 · `characterization/lib/normalize.mjs:174-187`(diff 기준 `<ul id="warnings">` 처리 추가) · 하네스가 새 동작 비교를 위해 바뀌었다. 베이스라인 커밋(4320bab)에서 스냅샷과 함께 생긴 것이라 "통과시키려고 고친" 증거는 없으나, 비교 기준을 바꾸는 변경이므로 사람이 한 번 확인해야 한다 · 수정 방향: 변경 이유를 PR 설명에 남긴다.**
5. **제안 · `modern/api/src/main/java/com/example/item/ItemSearchService.java:209, 222` · `NumberFormatException` 을 잡고 값만 바꿔 반환(로그 없음). PHP 캐스트 흉내라 의도된 폴백이지만 CLAUDE.md "예외는 삼키지 않는다"와 충돌할 수 있다 · 수정 방향: 폴백 의도를 주석으로 남기거나 debug 로그를 남긴다.**
6. **제안 · `ItemSearchService.java:65` · `log.debug` 는 건수만 남기고 사용자 입력 · 식별자는 남기지 않아 로그 규칙에 맞다. 별도 조치 없음.**

## 확인 필요

- `GET /api/units` 의 `itemCount` 추가가 이번 요청 범위에 포함됐는지(레거시 `units.php` 와 맞추려는 의도로 보이나 요청문에는 없음). `characterization/tests/example-units.test.js` 는 새 API 대상에서 `skipIf` 로 건너뛰어 동작 보존 여부를 검증하지 못했다.
- 현재 :8080 에 떠 있는 서버가 HEAD 빌드인지는 확인하지 못했다(실행 시작 12:20). 위 4건 실패는 소스 대조(`ItemSearchService.java:64-66`, 0건 문구 없음)로 원인이 설명되므로 서버가 낡았을 가능성은 낮다.
- 이관 후 프런트(`modern/web`)는 변경되지 않았다. `modern/web` 테스트는 실행하지 않음(대상 변경 없음).
