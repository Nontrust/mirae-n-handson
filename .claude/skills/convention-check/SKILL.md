---
name: convention-check
description: CLAUDE.md의 코딩 컨벤션·금지 사항 중 코드로 판정 가능한 항목을 점검한다. "컨벤션 점검", "커밋 전 점검", "리뷰 전에 확인해줘" 같은 요청에 쓴다.
argument-hint: "[점검할 경로 (생략 시 git status/diff에 잡힌 변경 파일)]"
allowed-tools: Read, Grep, Glob, Bash(git diff *), Bash(git status *)
---

# convention-check

modern/api, modern/web 코드가 CLAUDE.md의 코딩 컨벤션 · 금지 사항을 지키는지 점검한다.

## 점검 대상

- 인자로 경로가 주어지면 그 경로 아래 파일만 본다.
- 인자가 없으면 `git status --short`와 `git diff`로 잡히는 변경 파일을 대상으로 삼는다. `git status`의 `??`(아직 add 하지 않은 새 파일)도 포함한다.
- 대상 파일이 없으면 "점검할 변경 파일이 없습니다"라고만 답하고 끝낸다.

## 점검 항목 (코드로 예/아니오 판정 가능한 것만)

1. 계층 호출 방향 — Controller가 Repository를 직접 주입받거나 SQL을 실행하지 않는가
2. 도메인 경계 — 다른 도메인의 Repository를 직접 주입하지 않고 그 도메인 서비스를 거치는가
3. DTO 반환 — 컨트롤러가 엔티티를 그대로 반환하지 않고 record 기반 DTO를 반환하는가
4. 생성자 주입 — 필드에 `@Autowired`를 붙이지 않고 생성자 주입만 쓰는가
5. 예외 삼키지 않기 — 빈 `catch` 블록 없이, 잡은 예외를 로그 남기고 다시 던지거나 의미 있는 예외로 바꾸는가
6. 로깅 방식 — `System.out/err.println`, `e.printStackTrace()` 없이 SLF4J(`Logger`)만 쓰는가
7. 트랜잭션 경계 — `@Transactional` 메서드 안에서 외부 HTTP 호출(RestTemplate/WebClient 등)이나 긴 루프를 돌리지 않는가
8. 민감정보 로깅 금지 — 로그에 학생 식별자(`STU-…`)·이메일·토큰 값을 그대로 남기지 않는가

## 진행 절차

1. 점검 대상 파일 목록을 확정한다(위 규칙대로).
2. 각 파일을 Read로 읽고, 필요하면 Grep/Glob으로 관련 패턴(다른 도메인 import, `@Autowired`, `catch`, `println`, `printStackTrace`, `@Transactional` 주변 HTTP 클라이언트 호출, 로그 문자열 속 `STU-`/이메일 패턴 등)을 찾는다.
3. 항목 1~8을 파일별로 대조한다.
4. 코드를 직접 고치지 않는다. 위반을 찾아도 파일을 수정하지 말고 보고만 한다.

## 출력 형식 (이 순서 고정)

1. **판정 요약** — 몇 개 파일을 봤고, 위반 · 확인 필요 · 통과가 각각 몇 건인지 한두 문장으로.
2. **위반 목록 표** — `파일:줄번호 | 어긴 규칙 | 수정 방향` 열을 가진 표.
   - 위반이 없으면 표 대신 "위반 없음"이라고 쓴다.
   - "확인 필요" 항목도 같은 표에 넣되 수정 방향 칸에는 "확인 필요"라고 쓴다.

코드를 직접 고치지 않는다.
근거 라인을 댈 수 없는 지적은 하지 않고 "확인 필요"로 남긴다.
