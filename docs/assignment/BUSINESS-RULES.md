# assignment-thymeleaf 비즈니스 규칙 후보 — 배포 목록 화면

> 대상: `legacy/assignment-thymeleaf` — `GET /distributions`(배포 목록) + 화면 내 `POST /distributions/{id}/redistribute`(재배포)만 범위
> 먼저 읽음: `docs/assignment/ARCHITECTURE.md`, `docs/assignment/ERD.md`
> 원칙: 코드의 조건문·SQL로 직접 확인된 것만 "규칙"으로 올린다. 주석에만 있고 코드로 확인 안 되는 내용은 규칙으로 올리지 않고 비고에 "주석만 있음"으로 남긴다. 주석과 코드가 다르면 코드를 기준으로 쓰고 주석 내용은 비고에 남긴다.

**범위 관련 확인 사항**: "새 배포 등록"(`GET /distributions/new`, `POST /distributions`) 화면의 검증 로직(`DistributionController.create()`의 마감 검사, `DistributionService.distribute()`)은 다른 화면 소관이라 이 문서에서 규칙으로 올리지 않았다.

---

## A. 배포 목록 조회 (`DistributionDao.findAllForList`)

### BR-01
- **규칙**: 배포 목록은 연결된 과제 상태가 삭제(`'D'`)가 아닌 배포만 보여준다.
- **근거**: DistributionDao.java:31-36
- **근거 코드**:
```java
String sql = BASE_SELECT
        + " WHERE a.status <> 'D' "
        + " ORDER BY a.due_at ASC, d.id ASC";
```
- **확신도**: 확실
- **비고**: 없음.

### BR-02
- **규칙**: 목록은 연결된 과제의 마감 시각(`due_at`) 오름차순, 같으면 배포 id 오름차순으로 정렬된다.
- **근거**: DistributionDao.java:34
- **근거 코드**: 위 BR-01과 동일 SQL(`ORDER BY a.due_at ASC, d.id ASC`)
- **확신도**: 확실
- **비고**: 없음.

### BR-03
- **규칙**: 화면의 "제출 수"는 `submitted_at IS NOT NULL`인 제출만 센다 — 점수(`score`)만 있고 `submitted_at`이 없는 이상 데이터는 목록 화면의 제출 수에 포함되지 않는다.
- **근거**: DistributionDao.java:26
- **근거 코드**:
```sql
(SELECT COUNT(*) FROM submission s WHERE s.distribution_id = d.id AND s.submitted_at IS NOT NULL) AS submitted_cnt
```
- **확신도**: 확실
- **비고**: `findAllForList()`(목록)와 `findById()`(재배포 전/후 재조회) 두 경로 모두 같은 `BASE_SELECT`(DistributionDao.java:23-29) 하나를 공유하므로 집계 기준이 두 경로에서 갈라지지 않음을 확인했다.

### BR-04
- **규칙**: 화면의 "재배포" 버튼은 아직 재배포되지 않았거나(`redistributed == 0`), 과제 상태가 `'X'`(연장)인 배포에만 노출된다.
- **근거**: distributions.html:30-36
- **근거 코드**:
```html
<form class="inline" method="post" th:action="@{/distributions/{id}/redistribute(id=${d.id})}"
      th:if="${d.redistributed == 0 or d.assignmentStatus == 'X'}">
```
- **확신도**: 확실
- **비고**: 이 조건은 화면(버튼 노출) 기준일 뿐, 실제 재배포 가능 여부는 서버 쪽(`DistributionService.redistribute()`)에서 마감 경과·과제 상태 등을 다시 판단한다 — 아래 BR-11·BR-16 참조. 즉 버튼이 보여도 서버에서 거부될 수 있고, 반대로 버튼이 숨는 조합(재배포 완료 + 비연장 상태)에서만 가능한 서버 쪽 허용 경로(BR-16의 "시스템 오류 사유" 재허용)는 이 화면에서 도달할 방법이 없다.

---

## B. 재배포 입력 정리·사유 분류 (`DistributionService.redistribute` 0단계)

### BR-05
- **규칙**: 작업자(`operator`)가 비어 있으면 `"SYSTEM"`으로 대체하고, 20자를 넘으면 앞 20자로 자른다.
- **근거**: DistributionService.java:82-92
- **근거 코드**:
```java
if (op.length() == 0) {
    op = "SYSTEM";
    notes.add("작업자 미지정 → SYSTEM 으로 기록");
} else if (op.length() > 20) {
    op = op.substring(0, 20);
    warnings.add("작업자 ID가 20자를 넘어 잘렸습니다.");
}
```
- **확신도**: 확실
- **비고**: 매직 넘버 `20` — 매직 넘버 표 참조. 잘린 값이 DB에 저장되지는 않는다(감사 로그·메시지에만 쓰임, ARCHITECTURE.md 참조).

### BR-06
- **규칙**: 작업자 ID가 `"SYSTEM"`이 아니면서 `"teacher-"`나 `"admin-"`로 시작하지 않으면 경고만 표시하고 처리는 막지 않는다.
- **근거**: DistributionService.java:94-96
- **근거 코드**:
```java
if (!op.equals("SYSTEM") && !op.startsWith("teacher-") && !op.startsWith("admin-")) {
    warnings.add("작업자 ID 형식이 표준(teacher-, admin-)과 다릅니다: " + op);
}
```
- **확신도**: 확실
- **비고**: 없음.

### BR-07
- **규칙**: 재배포 사유(`reason`)는 줄바꿈·탭을 공백으로 바꾸고 연속 공백을 하나로 줄인 뒤, 비어 있으면 `"(사유 없음)"`으로, 200자를 넘으면 앞 197자 + `"..."`으로 대체한다.
- **근거**: DistributionService.java:99-114
- **근거 코드**:
```java
if (rsn.length() == 0) {
    rsn = "(사유 없음)";
    notes.add("재배포 사유가 비어 있어 기본 문구로 대체");
} else if (rsn.length() > 200) {
    rsn = rsn.substring(0, 197) + "...";
    warnings.add("재배포 사유가 200자를 넘어 잘렸습니다.");
}
```
- **확신도**: 확실
- **비고**: 매직 넘버 `200`/`197` — 매직 넘버 표 참조.

### BR-08
- **규칙**: 재배포 사유에 `<` 또는 `>`가 포함되면 각각 `&lt;`/`&gt;`로 치환한다.
- **근거**: DistributionService.java:115-118
- **근거 코드**:
```java
if (rsn.indexOf('<') >= 0 || rsn.indexOf('>') >= 0) {
    rsn = rsn.replace("<", "&lt;").replace(">", "&gt;");
```
- **확신도**: 확실
- **비고**: 이 치환된 문자열은 `result.getMessage()`를 통해 Thymeleaf `th:text`(layout.html:29)로 화면에 출력되는데, `th:text`는 기본적으로 자동 이스케이프하므로 `&lt;`가 다시 `&amp;lt;`로 이중 이스케이프되어 화면에는 리터럴 `&lt;`가 그대로 보일 가능성이 있다(코드상 이중 치환 여부까지는 화면 렌더링을 직접 실행해 확인하지 않아 "추정").

### BR-09
- **규칙**: 재배포 사유 문자열에 특정 키워드가 포함되면(우선순위 순서대로) 분류값(`reasonCategory`)을 매긴다 — "오류"/"장애"→1, "연장"/"추가"→2, "정정"/"수정"→3, "재시험"/"재평가"→4, 정리 후 사유가 정확히 `"(사유 없음)"`이면→9, 그 외 0(기타). 이 분류값은 뒤에서 재배포 2회차 허용(BR-16)·제출 유지 범위(BR-19) 판정에 직접 쓰인다.
- **근거**: DistributionService.java:119-130
- **근거 코드**:
```java
if (rsn.contains("오류") || rsn.contains("장애")) {
    reasonCategory = 1;
} else if (rsn.contains("연장") || rsn.contains("추가")) {
    reasonCategory = 2;
```
- **확신도**: 확실
- **비고**: 부분 문자열 포함(`contains`) 검사라서 "오류"가 사유 어디에 있든(예: "오류 아님") 분류 1로 잡힌다. 조건은 `if`/`else if` 순차 판단이라 여러 키워드가 동시에 있으면 먼저 매칭된 것 하나만 적용된다(예: "오류로 인한 재시험"은 1로만 분류됨, 4는 무시).

---

## C. 과제 상태별 재배포 가능성 (`DistributionService.redistribute` 1~2단계)

### BR-10
- **규칙**: 존재하지 않는 배포 id로 재배포를 시도하면 `E101`로 거부한다.
- **근거**: DistributionService.java:153-160
- **근거 코드**:
```java
if (dist == null) {
    result.setOk(false);
    result.setCode("E101");
```
- **확신도**: 확실
- **비고**: 없음.

### BR-11
- **규칙**: 배포에 연결된 과제(`assignment_id`)를 찾을 수 없으면 `E102`로 거부한다.
- **근거**: DistributionService.java:164-171
- **확신도**: 확실
- **비고**: `distribution.assignment_id`에 FK 제약이 있어(db/mariadb/init/01-schema.sql:101) 정상 운영 중에는 발생하기 어려운 방어 코드로 보인다(추정).

### BR-12
- **규칙**: 과제 상태가 `'D'`(삭제)이면 재배포를 `E103`으로 거부한다.
- **근거**: DistributionService.java:198-204
- **근거 코드**:
```java
case "D":
    statusLabel = "삭제";
    result.setOk(false);
    result.setCode("E103");
    result.setMessage("삭제된 과제는 재배포할 수 없습니다. [" + asg.getTitle() + "]");
```
- **확신도**: 확실
- **비고**: 없음.

### BR-13
- **규칙**: 과제 상태가 `'R'`(검수중)이면 재배포를 `E104`로 거부한다.
- **근거**: DistributionService.java:194-197, 215-221
- **근거 코드**:
```java
case "R":
    statusLabel = "검수중";
    underReview = true;
...
if (underReview) {
    result.setOk(false);
    result.setCode("E104");
```
- **확신도**: 확실
- **비고**: 없음.

### BR-14
- **규칙**: 과제 상태 코드가 `O`/`X`/`C`/`R`/`D` 외의 값(빈 문자열 포함)이면 "진행중"으로 간주해 처리를 계속하되 경고를 남긴다.
- **근거**: DistributionService.java:172-176, 205-213
- **근거 코드**:
```java
default:
    statusLabel = "알수없음(" + st + ")";
    warnings.add("알 수 없는 과제 상태 코드: " + st + " (진행중으로 간주)");
```
- **확신도**: 확실
- **비고**: `db/mariadb/init/01-schema.sql:87` 주석은 `assignment.status`를 "O=진행 C=마감" 두 값만 문서화한다 — 실제 코드가 처리하는 값 집합(O/X/C/R/D/기타)과 스키마 주석이 다르다(ERD.md 미확인 항목과 동일 이슈).

### BR-15
- **규칙**: 과제 상태가 `'X'`(연장)이면 이후 마감 판정을 완화해 재배포를 허용한다(마감이 지났어도 허용).
- **근거**: DistributionService.java:185-188, 314-316
- **근거 코드**:
```java
case "X":
    statusLabel = "연장";
    extended = true;
    notes.add("과제 상태 '연장' → 마감 판정을 완화합니다.");
...
} else if (extended) {
    allowed = true;
    notes.add("상태 '연장' → 마감 경과에도 재배포 허용");
```
- **확신도**: 확실
- **비고**: 없음.

---

## D. 마감 시각 기준 재배포 허용 (`DistributionService.redistribute` 4~5단계)

### BR-16
- **규칙**: 과제 상태가 연장(`X`)이 아닌 경우, 재배포는 **마감 후 72시간(3일) 이내**에만 허용된다. 72시간을 넘으면 `E105`로 거부한다.
- **근거**: DistributionService.java:307-325
- **근거 코드**:
```java
// 재배포는 마감 후 7일 이내에만 허용한다 (교무 지침 §4.2)
if (hoursAfterDue >= 72) {
    allowed = false;
    denyReason = "마감 후 유예 기간이 지났습니다. (" + dueText + ") 과제 상태를 '연장'으로 바꾼 뒤 다시 시도하세요.";
} else {
    allowed = true;
}
```
- **확신도**: 확실
- **비고**: **주석-코드 불일치.** 바로 위 주석은 "마감 후 **7일** 이내"(교무 지침 §4.2 인용)라고 돼 있지만, 실제 비교값은 `hoursAfterDue >= 72`, 즉 **3일(72시간)**이다. 7일(168시간)이 아니라 3일이 지나면 이미 거부된다 — 정책 문서(교무 지침 §4.2)와 실제 동작 중 어느 쪽이 맞는지는 코드만으로 판단할 수 없다. **사람 표본 점검 추천 대상.**

### BR-17
- **규칙**: 마감 시각(`due_at`)이 아직 지나지 않았거나(음수) 마감 당일(0~마감 경과 0시간 미만)이면 항상 재배포를 허용한다.
- **근거**: DistributionService.java:264-303, 310-313
- **근거 코드**:
```java
} else if (dueBucket <= 2) {
    allowed = true;
```
- **확신도**: 확실
- **비고**: `dueBucket 0~2`는 각각 "마감 전(3일 이상 남음)", "마감 임박(1~3일 남음)", "마감 당일"을 뜻한다(DistributionService.java:278-289) — 표시용 문구 분류일 뿐 허용 여부에는 0/1/2 구분 없이 동일하게 적용된다.

### BR-18
- **규칙**: 과제에 마감 시각(`due_at`)이 없으면 마감 규칙을 적용하지 않고 항상 재배포를 허용한다.
- **근거**: DistributionService.java:269-273, 310-311
- **근거 코드**:
```java
if (due == null) {
    hoursAfterDue = Long.MIN_VALUE;
    dueBucket = -1;
    dueText = "마감 없음";
    warnings.add("과제에 마감 시각이 없습니다. 마감 규칙을 적용하지 않습니다.");
```
- **확신도**: 확실
- **비고**: 스키마상 `assignment.due_at`은 `NOT NULL`(db/mariadb/init/01-schema.sql:86)이라 이 분기는 방어 코드로 보인다(추정) — DAO의 `LEFT JOIN unit`과 달리 `due_at` 자체가 NULL인 경로가 실제로 발생 가능한지는 확인하지 못했다.

---

## E. 재배포 2회차 이상 규칙 (`DistributionService.redistribute` 5단계)

### BR-19
- **규칙**: 이미 재배포된(`redistributed == 1`) 배포는 원칙적으로 다시 재배포할 수 없으며(`E106`), 과제 상태가 연장(`X`)이거나 사유 분류가 "시스템 오류"(`reasonCategory == 1`)일 때만 예외적으로 다시 허용한다.
- **근거**: DistributionService.java:329-345
- **근거 코드**:
```java
if (allowed && dist.getRedistributed() == 1) {
    if (extended) {
        notes.add("이미 재배포된 건이지만 '연장' 상태 → 재배포 다시 허용");
    } else if (reasonCategory == 1) {
        notes.add("이미 재배포된 건이지만 시스템 오류 사유 → 재배포 다시 허용");
    } else {
        allowed = false;
        denyReason = "이미 재배포된 배포입니다. 두 번째 재배포는 '연장' 상태이거나 사유가 시스템 오류일 때만 가능합니다.";
    }
}
```
- **확신도**: 확실
- **비고**: 이 검사는 `if (allowed && ...)`로 시작해 BR-16의 마감 판정에서 이미 `allowed=false`가 된 경우는 건너뛴다 — 즉 "마감 경과로 거부"와 "재배포 2회차라서 거부"는 서로 배타적으로만 평가되고 동시에 거부 사유가 합쳐지지 않는다.
- 이 재허용 경로("시스템 오류" 사유로 2회차 허용)는 배포 목록 화면에서 도달할 수 없다 — BR-04의 버튼 노출 조건이 `redistributed==0 OR status=='X'`라서, 이미 재배포됐고 상태가 연장이 아닌 배포는 버튼 자체가 렌더링되지 않아 이 사유 분류에 도달할 입력 폼이 없다(화면 범위에서 확인, API를 직접 호출하면 가능할 수 있음).

### BR-20
- **규칙**: 재배포 거부 시 반환 코드는 배포의 `redistributed` 값에 따라 결정된다(`redistributed==1`이면 `E106`, 아니면 `E105`) — 하지만 실제 거부 사유(`denyReason`)는 마감 경과(BR-16) 또는 2회차 제한(BR-19) 중 먼저 걸린 조건에서 정해진다.
- **근거**: DistributionService.java:339-345
- **근거 코드**:
```java
if (!allowed) {
    result.setOk(false);
    result.setCode(dist.getRedistributed() == 1 ? "E106" : "E105");
    result.setMessage("[" + title + " → " + className + "] 재배포 불가: " + denyReason);
```
- **확신도**: 확실
- **비고**: 이미 재배포된(`redistributed==1`) 배포가 **동시에** 마감 후 72시간도 지난 경우, 실제 거부 사유는 BR-16(마감 경과)에서 먼저 확정되어 메시지는 "마감 후 유예 기간이 지났습니다..."가 나가지만, 반환 코드는 `redistributed==1`이라는 이유만으로 `E106`(2회차 제한 코드)이 붙는다 — **코드와 메시지가 서로 다른 사유를 가리킬 수 있다.** 코드만으로 원인을 파악하려는 사람(운영자·로그 분석)에게는 오해 소지가 있다. **사람 표본 점검 추천 대상.**

---

## F. 제출 현황 집계 · 제출 유지 정책 (`DistributionService.redistribute` 6~7단계)

### BR-21
- **규칙**: 재배포는 기존 제출(`submission`) 데이터를 지우거나 새로 만들지 않는다 — 미제출자만 "재제출 대상"으로 계산해 화면 메시지에 안내한다.
- **근거**: DistributionService.java:483-486; ERD.md 확인(DistributionDao에 submission INSERT/DELETE 없음)
- **근거 코드**:
```java
// 재배포는 기존 제출을 지우지 않는다. 미제출자만 '재제출 대상' 으로 계산해 화면에 알려 준다.
int kept = total - notSubmitted;
int reopen = notSubmitted;
```
- **확신도**: 확실
- **비고**: 주석과 코드가 일치한다.

### BR-22
- **규칙**: 과제 상태가 연장(`X`)이면 미제출자뿐 아니라 "채점 전 제출자"(제출했지만 점수 없음)도 재제출 대상에 포함한다.
- **근거**: DistributionService.java:487-491
- **근거 코드**:
```java
if (extended) {
    reopen = notSubmitted + submittedNoScore;
    notes.add("연장 상태 → 채점 전 제출자 " + submittedNoScore + "명도 재제출 대상에 포함");
}
```
- **확신도**: 확실
- **비고**: 없음.

### BR-23
- **규칙**: 재배포 사유 분류가 "재평가"(`reasonCategory == 4`, 사유에 "재시험" 또는 "재평가" 포함)이면 **채점 완료자까지 포함한 전원**이 재제출 대상이 되며, 기존 점수는 그대로 유지된다.
- **근거**: DistributionService.java:492-496
- **근거 코드**:
```java
if (reasonCategory == 4) {
    reopen = total;
    notes.add("재평가 사유 → 전원 재제출 대상 (기존 점수는 유지)");
}
```
- **확신도**: 확실
- **비고**: `extended`(BR-22)와 `reasonCategory==4`(본 규칙)가 동시에 참이면 두 `if`가 순서대로 모두 실행되어 마지막에 설정된 `reopen = total`이 최종값이 된다(코드 순서상 재평가 규칙이 연장 규칙을 덮어씀) — 두 규칙이 동시에 성립할 때 우선순위가 재평가 쪽이라는 점은 조건문 순서로만 알 수 있고 별도 설명 주석은 없다.

### BR-24
- **규칙**: 채점 완료된 제출이 있고 사유가 "재평가"가 아니면, 해당 제출은 그대로 유지되며 점수 변경은 이 화면이 아니라 "성적 모듈"에서 처리하라고 안내한다.
- **근거**: DistributionService.java:497-499
- **근거 코드**:
```java
if (graded > 0 && reasonCategory != 4) {
    warnings.add("채점 완료 제출 " + graded + "건은 그대로 유지됩니다. 점수 변경이 필요하면 성적 모듈에서 처리하세요.");
}
```
- **확신도**: 확실
- **비고**: "성적 모듈"이 `legacy/grade-mssql`을 가리키는 것으로 보이나(추정), 코드에서 다른 모듈을 직접 참조하지는 않는다.

### BR-25
- **규칙**: 제출자의 학생 ID가 `"STU-"`로 시작하고 총 8자이며 접두어 뒤 4자리가 전부 숫자가 아니면(형식: `STU-1234`) "형식 이상"으로 집계하지만, 이 집계는 경고 문구에만 반영될 뿐 제출 자체를 막거나 제외하지 않는다.
- **근거**: DistributionService.java:377-398, 506-508
- **근거 코드**:
```java
} else if (!sid.startsWith("STU-")) {
    badStudentId++;
} else if (sid.length() != 8) {
    badStudentId++;
} else {
    ...
    if (!digits) {
        badStudentId++;
    }
}
```
- **확신도**: 확실
- **비고**: 매직 넘버 `8`(전체 길이), `4`(접두어 `"STU-"` 길이, `substring(4)` 기준) — 매직 넘버 표 참조.

### BR-26
- **규칙**: 제출 점수 기준 등급은 90점 이상 A, 80점 이상 B, 70점 이상 C, 60점 이상 D, 그 미만 F(F는 "60점 미만"으로도 집계)이다.
- **근거**: DistributionService.java:457-474
- **근거 코드**:
```java
if (sc.compareTo(new BigDecimal("90")) >= 0) {
    bandA++;
    grade = "A";
} else if (sc.compareTo(new BigDecimal("80")) >= 0) {
```
- **확신도**: 확실
- **비고**: 매직 넘버 `90/80/70/60` — 매직 넘버 표 참조. 점수가 0~100 범위를 벗어나도(예: 음수, 100 초과) 이 등급 분류 자체는 그대로 적용된다(범위 이탈은 별도로 경고만 함, BR-27).

### BR-27
- **규칙**: 점수가 0~100 범위를 벗어나면 경고를 남기지만 집계(평균·등급 등)에서 제외하지 않고 그대로 포함한다.
- **근거**: DistributionService.java:442-446
- **근거 코드**:
```java
if (sc.compareTo(BigDecimal.ZERO) < 0 || sc.compareTo(new BigDecimal("100")) > 0) {
    scoreOutOfRange++;
    warnings.add(sid + " : 점수 범위 이탈 (" + sc + ")");
}
sum = sum.add(sc);
```
- **확신도**: 확실
- **비고**: `sum.add(sc)`가 범위 검사와 무관하게 항상 실행되므로, 범위를 벗어난 점수도 평균 계산에 그대로 합산된다.

### BR-28
- **규칙**: 제출 시각(`submitted_at`)이 과제 마감(`due_at`)보다 늦으면 "지연" 제출로 집계한다. 마감 시각이 없는 과제는 지연 판정 자체를 하지 않는다.
- **근거**: DistributionService.java:428-434
- **근거 코드**:
```java
if (due != null && at.isAfter(due)) {
    late++;
    state = state + "/지연";
```
- **확신도**: 확실
- **비고**: 없음.

---

## G. 재배포 완료 처리 (`DistributionService.redistribute` 10단계)

### BR-29
- **규칙**: 재배포 플래그(`distribution.redistributed`) 갱신 쿼리가 0건 갱신되면 재배포 전체를 `E107`로 실패 처리한다.
- **근거**: DistributionService.java:620-626
- **근거 코드**:
```java
int updated = distributionDao.markRedistributed(distributionId);
if (updated == 0) {
    result.setOk(false);
    result.setCode("E107");
```
- **확신도**: 확실
- **비고**: 이 시점 이전에 이미 제출 집계(6~9단계)가 끝나 있으므로, `E107`이 발생해도 그 이전 단계의 부작용(예: 로그 출력)은 되돌리지 않는다 — 다만 `submission`/`distribution` 테이블에 대한 쓰기는 이 UPDATE 한 번뿐이라 데이터 정합성 문제로 이어지지는 않는다.

### BR-30
- **규칙**: 재배포 결과 코드는 경고(`warnings`)가 하나도 없으면 `OK`, 1~2건이면 `OK-W`, 3건 이상이면 `OK-W`+건수(`OK-W3` 등)로 표시한다.
- **근거**: DistributionService.java:685-691
- **근거 코드**:
```java
if (warnings.size() == 0) {
    result.setCode("OK");
} else if (warnings.size() <= 2) {
    result.setCode("OK-W");
} else {
    result.setCode("OK-W" + warnings.size());
}
```
- **확신도**: 확실
- **비고**: 매직 넘버 `2`(경고 2건까지는 건수 표시 없이 `OK-W`) — 매직 넘버 표 참조. 다만 `DistributionController.redistribute()`는 `result.getCode()`를 화면에 표시하지 않고 `message`/`warnings`만 플래시로 넘기므로(DistributionController.java:91-98), 이 코드 값 자체는 배포 목록 화면에는 보이지 않는다(로그에만 남음).

---

## 매직 넘버 표

| 값 | 위치 | 추정 의미 |
|---|---|---|
| `20` | DistributionService.java:90 | 작업자 ID 표시 최대 길이 — BR-05 |
| `200` / `197` | DistributionService.java:111-112 | 재배포 사유 최대 길이 / 자를 때 남기는 길이(`197+"..."=200`) — BR-07 |
| `72` | DistributionService.java:278(마감 전 3일 판정, 분 단위 `72*60`), 319(마감 후 재배포 허용 시간, 시간 단위) | **같은 숫자 `72`가 "마감 3일 전" 버킷 경계(분 단위 환산 전 72시간)와 "마감 후 72시간 재배포 허용" 두 군데에 각각 쓰인다** — 의미가 다른데 값이 같아 혼동 소지. 후자는 주석("7일")과도 다름(BR-16). |
| `24` | DistributionService.java:281 | 마감 임박 버킷 경계(24시간) |
| `168`(`24*7`) | DistributionService.java:293 | "유예 종료" 버킷 경계(7일) — 정작 재배포 허용 기준(BR-16)은 이 값이 아니라 72다. |
| `720`(`24*30`) | DistributionService.java:296 | "장기 경과" 버킷 경계(30일) |
| `40` | DistributionService.java:234 | 과제 제목 표시 최대 길이(자르고 `…` 추가) |
| `8` | DistributionService.java:383 | 학생 ID 전체 길이 기준(`STU-1234`) — BR-25 |
| `4` | DistributionService.java:386 | 학생 ID 접두어(`STU-`) 길이 — `substring(4)` 기준 — BR-25 |
| `10` | DistributionService.java:256 | 담당 교사 ID 표준 길이(`teacher-01` 형태) |
| `0` ~ `100` | DistributionService.java:443 | 허용 점수 범위 — BR-27 |
| `90`/`80`/`70`/`60` | DistributionService.java:458-467 | 등급 A/B/C/D 경계 — BR-26 |
| `100`(점수) | DistributionService.java:454 | 만점 판정 |
| `30` | DistributionService.java:672 | 요약 문자열에 나열하는 학생 목록 상한(초과 시 "외 N명") |
| `2` | DistributionService.java:687 | 경고 건수에 따른 결과 코드 분기(`OK-W` vs `OK-Wn`) — BR-30 |

---

## 사람이 표본 점검할 규칙 3개 추천

1. **BR-16 (마감 후 72시간 vs 주석의 "7일")** — 확신도는 "확실"(코드 자체는 명확히 72시간을 쓴다)이지만, 재배포 가능 여부를 가르는 핵심 정책이라 파급력이 크다. 주석이 인용하는 "교무 지침 §4.2"가 실제로 7일인지, 아니면 지침이 3일로 바뀌었는데 주석만 안 고친 것인지 담당자 확인이 필요하다.
2. **BR-20 (거부 코드 E105/E106이 실제 거부 사유와 어긋날 수 있음)** — 이미 재배포됐고 동시에 마감 후 72시간도 지난 배포는, 메시지는 "마감 경과"를 말하는데 코드는 `E106`(2회차 제한)이 찍힌다. 로그·운영 화면에서 코드만 보고 원인을 판단하는 사람이 있다면 오판할 수 있어 표본 점검이 필요하다.
3. **BR-19 / BR-04 (2회차 재배포의 "시스템 오류 사유" 예외가 이 화면에서 도달 불가능)** — 서비스 로직상 분명히 존재하는 예외 경로인데, 배포 목록 화면의 버튼 노출 조건(BR-04) 때문에 실제로는 이 화면을 통해 트리거할 방법이 없다. 의도된 설계(다른 화면/관리 도구로만 접근)인지, 죽은 코드인지 확인이 필요하다.
