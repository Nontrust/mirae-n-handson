# assignment-thymeleaf 데이터 흐름 (ERD) — 배포 목록 화면

> 대상: `legacy/assignment-thymeleaf` — `GET /distributions`(배포 목록) + `POST /distributions/{id}/redistribute`(화면 내 재배포)만 범위
> 먼저 읽음: `docs/assignment/ARCHITECTURE.md`
> 근거 자료: `db/mariadb/init/01-schema.sql`(스키마 정의), `legacy/assignment-thymeleaf` 소스
> `application.properties:2`의 기본 접속값(`jdbc:mariadb://localhost:3306/itembank`)이 `db/mariadb/init/01-schema.sql`의 `itembank` 스키마와 이름이 일치하고, 스키마 파일 자체 주석(01-schema.sql:73 "assignment-thymeleaf 모듈이 같은 DB를 쓴다")도 이를 뒷받침해 이 스키마를 근거로 삼았다.

## 1. 테이블 목록 (이 화면이 실제로 접근하는 것만)

| 테이블 | 주요 컬럼 | 근거 |
|---|---|---|
| `assignment` | `id` PK, `title` VARCHAR(200), `unit_id` INT FK→unit.id, `due_at` DATETIME, `status` CHAR(1) 기본값 'O' | db/mariadb/init/01-schema.sql:82-90 |
| `` `class` `` | `id` PK, `name` VARCHAR(50), `teacher_id` VARCHAR(20) | db/mariadb/init/01-schema.sql:75-80 |
| `distribution` | `id` PK, `assignment_id` INT FK→assignment.id, `class_id` INT FK→class.id, `distributed_at` DATETIME, `redistributed` TINYINT 기본값 0 | db/mariadb/init/01-schema.sql:92-103 |
| `submission` | `id` PK, `distribution_id` INT FK→distribution.id, `student_id` VARCHAR(20) ("STU-1001 형식" 주석), `submitted_at` DATETIME NOT NULL, `score` DECIMAL(5,1) NULL | db/mariadb/init/01-schema.sql:105-115 |
| `unit` (간접) | `id` PK, `code`, `name`, `grade` — `AssignmentDao.findById()`가 LEFT JOIN하지만 조회 결과 필드(`unit_code`/`unit_name`)를 이 화면 로직이 실제로 쓰지 않음(ARCHITECTURE.md 미확인 목록 참조) | db/mariadb/init/01-schema.sql:11-18; AssignmentDao.java:22-24 |

`unit`은 `legacy/item-bank-php` 소관 테이블이라(`docs/item-bank/ERD.md` 참조) 컬럼 상세는 이 문서에서 다시 정의하지 않고 FK 대상으로만 표시한다. `item`·`tag`·`item_tag`는 이 화면의 어떤 쿼리에서도 참조되지 않아 제외했다.

## 2. 테이블 관계

```mermaid
erDiagram
    unit {
        int id PK
        varchar code
    }
    assignment {
        int id PK
        varchar title
        int unit_id FK
        datetime due_at
        char status "기본값 O — 스키마 주석은 O/C만 언급하나 코드는 X/R/D/빈값도 처리(미확인 목록 참조)"
    }
    class {
        int id PK
        varchar name
        varchar teacher_id
    }
    distribution {
        int id PK
        int assignment_id FK
        int class_id FK
        datetime distributed_at
        tinyint redistributed "기본값 0"
    }
    submission {
        int id PK
        int distribution_id FK
        varchar student_id "STU-1001 형식(주석)"
        datetime submitted_at "스키마상 NOT NULL"
        decimal score "NULL 허용"
    }

    unit ||--o{ assignment    : "선언(FK assignment.unit_id -> unit.id, 01-schema.sql:89)"
    assignment ||--o{ distribution : "선언(FK distribution.assignment_id -> assignment.id, 01-schema.sql:101)"
    class ||--o{ distribution      : "선언(FK distribution.class_id -> class.id, 01-schema.sql:102)"
    distribution ||--o{ submission : "선언(FK submission.distribution_id -> distribution.id, 01-schema.sql:114)"
```

## 3. 읽기 · 쓰기 위치 표 (이 화면 범위만)

| 테이블 | 구분 | 파일:함수 | 근거 |
|---|---|---|---|
| `assignment` | 읽기(SELECT, JOIN) | `DistributionDao.findAllForList()` — 목록 조회 시 `distribution`과 JOIN, `status<>'D'` 필터 | DistributionDao.java:31-36 |
| `assignment` | 읽기(SELECT, JOIN) | `DistributionDao.findById()` — 재배포 갱신 전/후 단건 조회 시 JOIN | DistributionDao.java:38-44 |
| `assignment` | 읽기(SELECT, LEFT JOIN unit) | `AssignmentDao.findById()` — `redistribute()`가 과제 상태·마감·제목 확인용으로 호출 | AssignmentDao.java:41-48; DistributionService.java:164 |
| `assignment` | 쓰기 | 없음 — 이 화면 범위에서 `assignment`에 쓰는 코드 없음 | — |
| `` `class` `` | 읽기(SELECT, JOIN) | `DistributionDao.findAllForList()`/`findById()` — JOIN | DistributionDao.java:29 |
| `` `class` `` | 읽기(SELECT) | `ClassDao.findById()` — `redistribute()`가 배포 대상 학급명·담당교사 확인용으로 호출(없으면 대체 문자열 사용) | ClassDao.java:24-30; DistributionService.java:239 |
| `` `class` `` | 쓰기 | 없음 | — |
| `distribution` | 읽기(SELECT) | `DistributionDao.findAllForList()` — 목록 조회 | DistributionDao.java:31-36 |
| `distribution` | 읽기(SELECT) | `DistributionDao.findById()` — `redistribute()`가 갱신 전(153)·갱신 후(631) 두 번 호출 | DistributionDao.java:38-44 |
| `distribution` | 쓰기(UPDATE) | `DistributionDao.markRedistributed()` — `redistributed=1`로 갱신 | DistributionDao.java:65-67; DistributionService.java:620 |
| `submission` | 읽기(SELECT, 서브쿼리 COUNT) | `DistributionDao.findAllForList()`/`findById()` — `submitted_at IS NOT NULL`인 건수만 세는 상관 서브쿼리(`submitted_cnt`) | DistributionDao.java:26 |
| `submission` | 읽기(SELECT) | `DistributionDao.findSubmissions()` — `redistribute()`가 제출 현황 집계(미제출/채점전/채점완료, 등급 등)에 사용 | DistributionDao.java:69-73; DistributionService.java:348 |
| `submission` | 쓰기 | 없음 — 재배포는 `submission` 행을 지우거나 만들지 않는다(제출 유지 정책, ARCHITECTURE.md 참조) | DistributionService.java:484 주석 |
| `unit` | 읽기(간접, LEFT JOIN) | `AssignmentDao.findById()` — 조회는 되지만 `redistribute()` 로직이 결과값(`unit_code`/`unit_name`)을 사용하지 않음 | AssignmentDao.java:22-24 |
| `unit` | 쓰기 | 없음 | — |

목록 조회(`findAllForList`)와 건수 표시(같은 목록 화면의 `submitted_cnt` 컬럼)는 별도 쿼리가 아니라 **같은 SQL의 서브쿼리 하나**로 처리된다(DistributionDao.java:26) — 경로가 갈라지지 않는다.

## 4. 미확인

- `unit` 테이블의 나머지 컬럼(`grade` 등)과 `item`/`tag`/`item_tag`는 이 화면이 참조하지 않아 상세 확인하지 않았다 — 필요하면 `docs/item-bank/ERD.md` 참조.
- `assignment.status`, `distribution.redistributed`, `submission.score`를 실제로 변경하는 다른 모듈(예: `grade-mssql`, 학생 제출 화면)이 있는지는 `legacy/assignment-thymeleaf` 범위 밖이라 확인하지 못했다.
- `submission.student_id`의 "STU-1001" 형식이 DB 레벨 제약(CHECK 등)으로 강제되는지 스키마에서 확인했으나 없음 — 애플리케이션 코드(`DistributionService.java:377-397`)에서만 형식 검사(경고만, 저장 차단 아님)를 한다.
