판정: 반려

- 검증 대상: `vendor-prs/pr-1-missing-tests.patch` (외주 협력사, 2026-09-22)
- 요청 범위: "단원별 문항 수 통계 API 추가" — patch 맨 위 Subject 의 요청 문장(실습 3 안내대로 `head` 출력에서 읽음).
- 기준 브랜치: `upstream/main`. patch 의 UnitService 기준 blob `2a60112` 는 `upstream/main` 의 blob 과 같다. 검토 브랜치 `review/pr-1`(커밋 `6358bbe`, `HEAD~1..HEAD`)에서 내용을 읽었다.
- 방식: 내용만 읽었다. 테스트는 실행하지 않았다(지시사항).
- 줄번호 표기: `patch:N` 은 patch 파일의 줄, `파일:N` 은 `review/pr-1` 에서 그 파일의 줄이다.

## 기준별 결과

| 기준 | 결과 | 근거 |
|---|---|---|
| 1. 테스트 통과 | 미충족 | 테스트는 실행하지 않음(지시사항). 실행 결과는 "확인 필요"로 둔다. 다만 "테스트가 충분하다" 항목은 실행하지 않아도 판정할 수 있고, 미충족이다. 변경 파일 3개가 모두 `src/main` 이고(`patch:16-19`), 새 엔드포인트와 `countActiveItemsByUnit()` 를 다루는 테스트가 없다. PR 본문은 테스트 통과만 주장한다(`patch:14`). 지우거나 `@Disabled` 처리한 기존 테스트는 없다. |
| 2. 치명 이슈 0건 | 충족 | 비밀값 리터럴 없음. SQL 문자열 연결 없음(파생 쿼리 `countByUnitIdAndStatus` 사용). 데이터를 지우거나 덮어쓰는 코드 없음(읽기 전용 조회). |
| 3. 요청 범위 이탈 없음 | 충족 | 변경 파일 3개(Controller · 응답 DTO · Service)는 모두 요청 문장으로 설명된다. 의존성 · 스키마 · 설정 변경 없음. |
| 4. 컨벤션 준수 | 충족 | Controller 는 Service 만 호출한다(`UnitController.java:27-28`). 응답은 record DTO 다(`UnitItemCountResponse.java:4`). 생성자 주입(`UnitService.java:18-21`), SLF4J 로그, `/api/units` 아래 경로, 상태 코드(`ItemStatus.ACTIVE`) 기준 판단을 지켰다. 빈 catch · 임시 출력 없음. 테스트 누락은 기준 1 에서 다룬다. |

## 이슈 목록

1. **경고 · patch 전체(`patch:16-19`) · 새 기능에 테스트가 없다.**
   근거: PR 본문(`patch:12`)이 내세우는 두 가지 동작에 대한 테스트가 없다. 하나는 "단원 목록과 같은 순서", 다른 하나는 "문항 0개 단원 포함"이다. CLAUDE.md 는 새 조회 API 를 만들 때 테스트도 같은 도메인 패키지에 만들라고 정해 두었다. `review/pr-1` 의 `src/test/java/com/example/item/` 에는 `UnitController` · `countActiveItemsByUnit` 테스트가 없다.
   수정 방향: 테스트 두 종류를 추가한다. 하나는 `UnitServiceTest` 에 넣는 `countActiveItemsByUnit` 단위 테스트다(순서 유지, 0개 단원 포함, 단원 없음 → 빈 목록). 다른 하나는 `GET /api/units/item-counts` 웹 슬라이스 테스트다(200, JSON 필드명). 테스트 이름은 camelCase 로 짓고 `@DisplayName` 에 한국어 설명을 붙인다.

2. **경고 · `UnitService.java:37-39` · 단원 수만큼 count 쿼리가 나간다(N+1).**
   근거: 단원마다 `itemRepository.countByUnitIdAndStatus(...)` 를 한 번씩 부르므로, 단원 N 개면 쿼리가 1 + N 번 나간다. 클래스 수준 `@Transactional(readOnly = true)`(`UnitService.java:10`) 안이라 그동안 커넥션 하나를 계속 쥐고 있다. CLAUDE.md 는 풀 크기를 `maximum-pool-size: 5` 로 작게 맞춰 두었다고 적고 있다.
   수정 방향: `unit_id` 로 묶는 집계 쿼리(`group by`) 한 번으로 바꾸고 결과를 `unitId → 개수` 맵으로 합친다. 맵에 없는 단원은 0 으로 둔다.

3. **제안 · `UnitItemCountResponse.java:4` · 응답에 단원 `id` 가 없다.**
   근거: 같은 패키지의 `UnitResponse` 는 `id` 를 포함한다(`UnitResponse.java:4`). 새 DTO 는 `code` · `name` · `grade` 만 담는다. 화면에서 두 응답을 합칠 때 키가 `code` 뿐이다.
   수정 방향: 필드 구성을 `UnitResponse` 와 맞출지 요청자와 정한다.

## 확인 필요

- **테스트 실행 결과**: 실행하지 않음(지시사항). `review/pr-1` 에서 `cd modern/api && ./gradlew test` 를 실행해 통과 · 실패 수를 확인해야 한다.
- **이관 여부**: 이 기능에 대응하는 레거시 동작이 있는지는 patch 만으로 알 수 없다. 이관 코드라면 `characterization` 의 `npm test` 를 레거시 대상과 새 API 대상으로 모두 실행해야 한다.
- **`day1` 과의 충돌**: patch 는 `upstream/main` 기준이라 `review/pr-1` 에는 깨끗하게 적용된다. 하지만 `day1` 에는 적용되지 않는다(`git apply --check` 결과 `patch failed: .../UnitService.java:13`). `day1` 의 1회차 이관(커밋 `307b0e5`)이 `UnitService` 를 바꿨고, `GET /api/units` 응답에 같은 의미의 `itemCount`(BR-24)를 넣었기 때문이다. 두 변경을 합칠 때는 집계 로직이 두 곳에 생기지 않게 정리해야 한다. 이것은 외주 PR 결함이 아니라 머지 시점의 과제다.

## 정정 기록

- 1차 판정은 `day1` 작업 트리를 기준으로 읽어서 기준을 잘못 잡았다. 그때 올린 두 이슈를 이번 판정에서 이슈 목록에서 뺐다.
  - 경고 "patch 가 현재 브랜치에 적용되지 않는다": `upstream/main` 기준에서는 해당하지 않는다. "확인 필요"의 `day1` 충돌 항목으로 옮겼다.
  - 경고 "같은 집계 로직 중복": `upstream/main` 의 `listUnits()` 는 문항 수를 세지 않는다(`UnitService.java:24-30`). 이것도 `day1` 충돌 항목으로 옮겼다.
- 위 정정으로 기준 4 를 미충족에서 충족으로 바꿨다. 최종 판정(반려)과 그 사유인 기준 1 의 테스트 부족은 그대로다.
