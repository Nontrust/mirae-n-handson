# 외주사 PR 판정 기록

판정 기준은 `templates/approval-checklist.md` 다. 코드는 수정하지 않고 판정만 적는다.

| PR | 판정 | 미충족 기준 | 근거(파일:줄번호) | 외주사에 돌려보낼 수정 요청 |
|---|---|---|---|---|
| vendor pr-1 `단원별 문항 수 통계 API 추가` (`vendor-prs/pr-1-missing-tests.patch`, 1a29f03) | 반려 | 1. 테스트 통과(테스트가 충분하지 않음) · 4. 컨벤션 준수(새 조회 API는 테스트와 함께 만든다) | `vendor-prs/pr-1-missing-tests.patch:16-18` 변경 파일 3개가 모두 `src/main` 이고 `src/test` 변경이 0건이다. 새 코드 `UnitService.countActiveItemsByUnit()`(patch:78-85)·`UnitController.itemCounts()`(patch:32-35)를 검증하는 테스트가 없다(`countActiveItemsByUnit`·`item-counts` 가 `src/test` 에 없음). 커밋 메시지(patch:14)의 "기존 테스트 전부 통과"는 기존 24건이 통과했다는 뜻일 뿐 새 기능은 검증하지 않는다. 경고: patch:79-81 단원마다 `countByUnitIdAndStatus` 를 호출해 단원 수 + 1 번 쿼리가 나간다(N+1). | 1) `UnitServiceTest` 에 `countActiveItemsByUnit` 테스트를 추가한다: 단원 순서(학년, 코드) 유지, 문항 0개 단원이 0으로 포함, 공개 상태(`ItemStatus.ACTIVE`)로만 센다. 2) 컨트롤러 슬라이스 테스트로 `GET /api/units/item-counts` 의 200 응답과 필드(`code`, `name`, `grade`, `activeItemCount`)를 확인한다. 3) (경고) 단원별 반복 쿼리 대신 `unit_id` 그룹 쿼리 한 번으로 세고 0개 단원은 서비스에서 0으로 채운다. 쿼리 추가가 필요하면 그 사실을 PR 설명에 적는다. |
| vendor pr-1 `단원별 문항 수 통계 API 추가` (커밋 `6358bbe` = `review/pr-1` HEAD, `main` 기준 patch 적용본) | 반려 | 1. 테스트 통과(테스트가 충분하지 않음) · 4. 컨벤션 준수(새 조회 API는 테스트와 함께 만든다) | `modern/api/src/main/java/com/example/item/UnitService.java:36` · `UnitController.java:26-27` 새 코드를 검증하는 테스트가 `src/test` 에 0건이다(`item-counts` · `countActiveItemsByUnit` grep 0건, 변경 파일 3개 모두 `src/main`). 실행: `./gradlew test --rerun-tasks` 24 통과 · 0 실패 · 0 오류 · 0 skip — 기존 테스트만 통과하고 새 기능은 검증되지 않는다. 경고: `UnitService.java:38-39` 단원마다 `countByUnitIdAndStatus` 호출(N+1). | 1) `UnitServiceTest` 에 단원 순서 유지 · 0개 단원 포함 · `ItemStatus.ACTIVE` 만 집계 테스트 추가. 2) `GET /api/units/item-counts` 200 응답과 필드(`code`, `name`, `grade`, `activeItemCount`) 컨트롤러 슬라이스 테스트 추가. 3) (경고) `unit_id` 그룹 쿼리 한 번으로 세고 0개 단원은 서비스에서 0으로 채운다. |

## 검증 메모 (pr-1)

- 요청 범위: "단원마다 공개 상태 문항 수를 한 번에 볼 수 있는 `GET /api/units/item-counts` 추가". 변경 파일 3개(`UnitController`, `UnitItemCountResponse`, `UnitService`)가 모두 이 문장으로 설명된다. 의존성 · 스키마 · 설정 변경은 없다 → 3번 충족.
- 치명 이슈 없음: 비밀값 · SQL 문자열 결합 · 삭제 코드 없음(Spring Data 파생 쿼리 사용) → 2번 충족.
- 컨벤션: 컨트롤러는 서비스만 호출, 생성자 주입, record DTO, SLF4J 사용, 같은 도메인의 `ItemRepository` 주입은 허용 범위. 단 테스트 누락은 CLAUDE.md "새 조회 API는 … 테스트를 같은 도메인 패키지에 만든다"에 어긋난다.
- 실행한 테스트: `upstream/main` 기준 임시 worktree 에 patch 를 적용(`git apply --check` 통과)하고 `cd modern/api && ./gradlew test` 실행 → 24 통과 · 0 실패 · 0 오류 · 0 skip. 새 테스트는 0건이다. `characterization` 과 `modern/web` 은 이 PR 이 건드리지 않아 실행하지 않음. worktree 는 검증 뒤 삭제했다.
- 검증 대상 정정: 호출 인자의 `HEAD~1..HEAD` 는 `docs/verify/migration-review-2.md` 한 파일만 추가하는 커밋이라 외주사 코드가 들어 있지 않다. 외주사 변경분은 `vendor-prs/pr-1-missing-tests.patch` 이므로 그 patch 를 기준으로 판정했다.

- 재검증(커밋 `6358bbe` 직접 실행): 위 두 번째 행. 요청 범위는 외주사 patch Subject 문장이며 요청자가 "맞다"고 확인했다. 변경 3개 파일이 모두 이 문장으로 설명되고 의존성 · 스키마 · 설정 변경 · 치명 이슈는 없다. 판정은 첫 행과 같다.
