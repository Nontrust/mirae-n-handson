# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

이 문서는 현행 구현 `modern/api`(Spring Boot 3 · Java 21)와 `modern/web`(React 18 · TypeScript · Vite)에 적용된다. `legacy/`, `characterization/`, `mcp-skeleton/` 등 다른 디렉터리의 명령 · 규칙은 이 문서에서 다루지 않는다.

답변과 이 저장소에 쓰는 문서는 한국어로 쓴다.

## 빌드 · 테스트 명령

```bash
# modern/api (Spring Boot 3 · Java 21 · Gradle)
cd modern/api && ./gradlew test                                            # 단위 · 슬라이스 테스트, H2 대상 (DB 컨테이너 불필요)
cd modern/api && ./gradlew test --tests com.example.item.ItemServiceTest   # 테스트 클래스 하나만
cd modern/api && ./gradlew build                                           # 테스트 포함 전체 빌드
cd modern/api && ./gradlew bootRun                                         # 로컬 실행 — 먼저 `docker compose --profile modern up -d` 로 MariaDB를 띄운다

# modern/web (React 18 · TypeScript · Vite)
cd modern/web && npm ci
cd modern/web && npm run dev
cd modern/web && npm run lint
cd modern/web && npm run typecheck
cd modern/web && npm test                                                  # vitest run
cd modern/web && npx vitest run src/components/ItemTable.test.tsx          # 테스트 파일 하나만
cd modern/web && npm run build
```

- 동작을 바꾼 뒤에는 반드시 해당 모듈의 테스트를 실행하고, 통과 · 실패 수를 답변에 적는다. 실행하지 않았으면 "실행하지 않음"이라고 적는다.

## 코딩 컨벤션

- 계층은 Controller → Service → Repository 순으로 호출한다. 컨트롤러는 서비스만 호출하고, Repository를 직접 주입받거나 SQL 문자열을 실행하지 않는다.
- 다른 도메인의 데이터가 필요하면 그 도메인의 서비스를 통해 접근한다. 다른 도메인의 Repository를 직접 주입하지 않는다.
- 요청 · 응답은 record 기반 DTO로 만든다. 엔티티를 컨트롤러 밖으로 그대로 반환하지 않는다.
- 한 클래스는 한 가지 역할만 맡는다: HTTP 처리(Controller) · 트랜잭션과 도메인 로직(Service) · 영속성 접근(Repository)을 한 클래스에 섞지 않는다.
- 같은 로직을 두 곳 이상에 복사해 쓰지 않는다. 이미 있는 메서드 · 유틸리티가 있으면 그것을 호출하고, 없으면 공통 메서드로 뽑아 재사용한다.
- 의존성 주입은 생성자 주입만 쓴다. 필드에 `@Autowired`를 붙이지 않는다.
- 패키지는 계층이 아니라 도메인(기능) 단위로 나눈다(`com.example.item`, `com.example.assignment`, `com.example.common`).
- Java 21 문법을 쓴다: DTO는 record, 다중 분기는 switch 표현식(화살표 문법), 타입 검사 뒤 캐스팅은 패턴 매칭(`if (x instanceof Foo f)`)으로 쓴다.
- 예외는 잡은 자리에서 삼키지 않는다. 잡았으면 로그를 남기고 다시 던지거나 의미 있는 예외로 바꿔 던진다.
- 로그는 SLF4J(`org.slf4j.Logger`)로만 남긴다. 로그 레벨: 정상 흐름은 INFO 이하, 복구 가능한 실패는 WARN, 요청을 실패시키는 예외는 ERROR.
- 예외 → HTTP 응답 변환은 `@RestControllerAdvice`(`GlobalExceptionHandler`) 한 곳에서만 한다: 리소스 없음 404, 검증 실패 400, 상태 충돌 409, 그 외 500.
- 테스트 이름은 확인하는 동작이 드러나는 camelCase로 짓고 `@DisplayName`에 한국어 설명을 붙인다.
- modern/web의 데이터 조회는 `src/api/client.ts`의 `getJson()`과 `src/hooks/useApiQuery.ts`의 `useApiQuery(key, load)`만 거친다. 컴포넌트는 그 결과(`QueryState`)를 `<AsyncSection>`으로 그리고, 개별 컴포넌트마다 조회용 `useState`/`useEffect`를 새로 만들지 않는다.

## 금지 사항

- `legacy/`는 분석 · 이관 대상이다. 허락 없이 수정하지 않는다.
- 의존성 추가(`build.gradle`의 `dependencies`, `package.json`의 `dependencies`/`devDependencies`)는 먼저 묻는다.
- DB 스키마 변경(테이블 · 컬럼 · 인덱스 추가/삭제, 마이그레이션 파일 추가)과 시드 데이터 변경은 먼저 묻는다.
- `application.yml`의 DB 접속 정보 · 커넥션 풀 설정(`maximum-pool-size` 등)을 바꾸지 않는다.
- 실제 비밀값(운영 계정 · 토큰 · 키)을 코드 · 설정 파일에 리터럴로 넣지 않는다. 실습용 더미 값(`app-pass` 등)은 예외다.
- 운영 DB 호스트에 접속하는 명령 · 설정을 만들지 않는다.
- `@Transactional` 메서드 안에서 외부 HTTP 호출이나 긴 루프를 돌리지 않는다.
- 빈 `catch` 블록, `System.out.println`/`System.err.println`/`e.printStackTrace()`를 쓰지 않는다.
- 로그에 학생 식별자(`STU-…`) · 이메일 · 토큰 값을 그대로 남기지 않는다.
- 요청받지 않은 파일을 포맷팅 · import 정리 명목으로 고치지 않는다.

## 완료 기준

- 이관 · 리팩토링 작업은 `characterization`의 `npm test`가 전부 통과하기 전에는 완료로 보고하지 않는다.
- 테스트가 실패하면 실패한 케이스와 그 차이를 그대로 보고한다. 요약해서 "거의 됐다"고 말하지 않는다.
- 테스트를 통과시키려고 `characterization/`의 테스트 코드나 스냅샷 파일을 고치지 않는다. 스냅샷을 바꿔야 한다고 판단되면 멈추고 묻는다.
- 레거시 동작이 버그로 보여도 이관 중에는 고치지 않는다. 대신 "의심 동작" 목록으로 따로 보고한다.

## 아키텍처 안내

```
modern/api/src/main/java/com/example/
├── item/          문항 · 단원 · 태그 (ItemController, ItemService, ItemRepository, Item, Unit)
├── assignment/    과제 배포 · 재배포 · 학급 리포트 (Distribution*, Report*)
├── common/        GlobalExceptionHandler, 공통 응답(ErrorResponse) · 예외(NotFoundException)
└── config/        WebConfig(CORS), ClockConfig(테스트용 Clock 빈)
```

- 기존 엔드포인트: `GET /api/units`, `GET /api/units/{code}/items`, `GET /api/items/{id}`, `GET /api/distributions/{id}`, `POST /api/distributions/{id}/redistribute`, `GET /api/classes/{id}/report`. 새 엔드포인트는 `/api/<도메인 복수형>` 아래에 둔다.
- 공개 문항만 노출한다(`status = 'A'`). 삭제 플래그가 아니라 상태 코드로 판단한다.
- `open-in-view: false`이므로 서비스는 트랜잭션 안에서 엔티티를 DTO로 바꿔 반환한다. 트랜잭션 밖에서 지연 연관관계를 건드리면 예외가 난다.
- `Clock`은 `LocalDateTime.now()`를 직접 부르지 않고 `ClockConfig`가 주입하는 빈을 쓴다 — 테스트에서 시각을 고정하기 위함이다.
- CORS는 `/api/**`에 대해 Vite 개발 origin(`http://localhost:5173`)의 `GET`만 허용한다(`WebConfig`).
- `application.yml`의 HikariCP 풀은 운영 값과 같게 작게 맞춰 두었다(`maximum-pool-size: 5`) — 트랜잭션 · 커넥션을 오래 쥐는 코드는 풀을 빠르게 고갈시킨다.
- modern/web은 `src/api/`(HTTP 호출) → `src/hooks/`(조회 상태 관리) → `src/components/`(렌더링) 세 계층으로 나뉜다. 새 조회 화면도 이 구조를 따른다.
- 새 조회 API는 컨트롤러 → 서비스 → 리포지토리 세 파일과 테스트를 같은 도메인 패키지에 만든다.
