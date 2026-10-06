판정: 반려

- 검증 대상: `upstream/main...HEAD` (커밋 4개: 4320bab, df03263, 307b0e5, 2110f93)
- 요청 범위: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관.
- 이관된 엔드포인트: `GET /api/items/search` (legacy `search.php`)
- 1차 리뷰(`docs/verify/migration-review-1.md`) 대비: 치명 이슈(0건 안내 문구 누락)는 2110f93 에서 해소됐다. 반려 사유는 **요청 범위 이탈**이 그대로 남아 있기 때문이다.

## 기준별 결과

| 기준 | 결과 | 근거 |
|---|---|---|
| 1. 테스트 통과 | 충족(경고 1건) | 실제 실행함. `modern/api` 41 통과 · 0 실패 · 0 오류 · 0 skip. characterization 레거시 대상 27 통과. 새 API 대상(HEAD 빌드, :8082) 26 통과 · 0 실패 · 1 skip(`example-units`, 레거시 전용 `skipIf`). 기존 테스트를 지우거나 건너뛰게 바꾼 곳 없음. 다만 `/api/units` 의 `itemCount` 는 새 API 대상 characterization 이 건너뛰어 동작 보존이 검증되지 않았다(이슈 2) |
| 2. 치명 이슈 0건 | 충족 | 비밀값 · SQL 문자열 결합(Specification 바인딩 사용) · 데이터 삭제 코드 없음. 1차의 치명 이슈는 `ItemSearchService.java:67-69` 와 단위 테스트 `ItemSearchServiceTest.java:192-193` 로 해소 확인 |
| 3. 요청 범위 이탈 없음 | **미충족** | 요청 문장은 확인됨. 그러나 `GET /api/units` 응답 변경(`UnitResponse` · `UnitService` · `UnitServiceTest`)과 `CLAUDE.md` · `.claude/skills/*` · `docs/*`(문서 · Skill) 추가가 "엔드포인트 1개 이관"으로 설명되지 않는다. 의존성 · 스키마 · 설정 파일 변경은 없음 |
| 4. 컨벤션 준수 | 충족(제안 1건) | 컨트롤러 → 서비스 → 리포지토리, 생성자 주입, record DTO, SLF4J, `System.out`/`console.log` 없음, 빈 catch 없음. 로그에 학생 식별자 · 입력값 없음 |

## 실행한 테스트

| 명령 | 결과 |
|---|---|
| `cd modern/api && ./gradlew test --rerun-tasks` | 41 통과 · 0 실패 · 0 오류 · 0 skip |
| `cd characterization && npm test` (레거시 :8081) | 27 통과 · 0 실패 |
| `cd characterization && TARGET_BASE_URL=http://localhost:8082 npm test` | 26 통과 · 0 실패 · 1 skip |

- **재검증(12:52 이후):** :8080 이 12:52 에 재기동돼 HEAD(2110f93, 12:49) 이후 빌드임을 확인하고 :8080 으로 다시 실행했다. `modern/api` 41 통과 · 0 실패, 레거시 27 통과, 새 API(:8080) 26 통과 · 1 skip(`example-units`). 결과와 판정은 변하지 않았다.
- 새 API 대상은 :8080 이 아니라 **HEAD 를 새로 `bootRun` 한 :8082** 로 실행했다. :8080 서버는 12:20 에 떴고 수정 커밋(2110f93)은 12:49 라 낡은 빌드였기 때문이다. :8082 는 실행 뒤 종료했다. :8080 에 대한 결과는 이 문서에 쓰지 않는다.
- `modern/web` 은 변경이 없어 실행하지 않음.
- 코드 · 스냅샷은 수정하지 않았다(검증 전후 `git status` 동일).

## 이슈 목록

1. **경고 · `modern/api/src/main/java/com/example/item/UnitService.java:26` · 이관 대상이 아닌 `GET /api/units` 에 `itemCount` 를 추가(`UnitResponse.java:7`)하고 단원마다 `countByUnitIdAndStatus`(`ItemRepository.java:26`)를 호출해 단원 수만큼 쿼리가 나간다(N+1). 요청은 엔드포인트 1개 이관인데 기존 엔드포인트의 응답 모양이 바뀌었다 · 수정 방향: 요청에 포함된 변경인지 확인하고, 아니면 별도 PR 로 분리한다. 유지한다면 그룹 쿼리 한 번으로 센다. (1차 이슈 2 — 미해결)**
2. **경고 · `characterization/tests/example-units.test.js:21` · `units` 응답 변경의 동작 보존을 새 API 대상으로 검증하는 테스트가 없다(`it.skipIf(legacyOnly)`). 단위 테스트(`UnitServiceTest.java`)는 저장소를 mock 한 값만 확인한다 · 수정 방향: 이슈 1 의 변경을 유지한다면 새 API 대상 비교가 가능한 테스트를 마련한다. 스냅샷은 사람이 판단해 바꾼다.**
3. **경고 · `CLAUDE.md`, `.claude/skills/{convention-check,document-module,impact}/SKILL.md`, `docs/assignment/*`, `docs/item-bank/*`, `docs/verify/migration-review-1.md` · 변경 파일 25개 중 약 14개가 이관 요청으로 설명되지 않는다. `upstream/main` 이 옛 커밋(1bb4f2f)이라 diff 에 함께 잡힌 것일 수 있다 · 수정 방향: 이 PR 에 포함할 범위인지 확인하고, 아니면 분리하거나 기준 브랜치를 갱신한다. (1차 이슈 3 — 미해결)**
4. **경고 · `characterization/lib/normalize.mjs:177-186` · 비교 하네스가 새 동작 비교를 위해 바뀌었다(`<ul id="warnings">` 를 `message` 에 합침). 베이스라인 커밋(4320bab)에서 스냅샷과 함께 생겼고 통과시키려고 고친 증거는 없으나 비교 기준을 바꾸는 변경이다 · 수정 방향: 변경 이유를 PR 설명에 남기고 사람이 한 번 확인한다. (1차 이슈 4 — 미해결)**
5. **제안 · `modern/api/src/main/java/com/example/item/ItemSearchService.java:215, 228` · `NumberFormatException` 을 잡고 로그 없이 값만 바꿔 반환한다. PHP 캐스트를 흉내 낸 의도된 폴백이지만 CLAUDE.md "예외는 삼키지 않는다"와 충돌할 수 있다 · 수정 방향: 폴백 의도를 주석으로 남기거나 debug 로그를 남긴다. (1차 이슈 5)**
6. **제안 · `characterization/tests/item-bank.test.js` · 13개 케이스가 검색 화면만 다룬다. 기본값(`level` 빈 값이면 5 제외)과 정렬(`sort=unit` 의 2차 기준 등)은 일부만 스냅샷에 드러난다 · 수정 방향: 정렬 조합(`sort=title&dir=desc`, `sort=unit`) 케이스를 추가하면 이관 보존 범위가 넓어진다. 판정에 영향 없음.**

## 확인 필요

- `GET /api/units` 의 `itemCount` 추가(이슈 1)가 이번 요청에 포함됐는지. 레거시 `units.php` 와 맞추려는 의도로 보이나 요청문에는 없다.
- 이슈 3 의 문서 · Skill 파일이 의도적으로 같은 PR 에 들어가는지, 아니면 `upstream/main` 이 낡아서 섞인 것인지.
- 미추적 파일 `.claude/skills/verify/`, `legacy/item-bank-php/docs/` 는 diff 범위(`upstream/main...HEAD`) 밖이라 이번 검증에서 보지 않았다.
