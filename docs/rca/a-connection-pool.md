# A. 커넥션 풀 장애 원인 분석

대상 로그: `incident-logs/a-connection-pool/` (줄번호는 모두 이 디렉터리 기준)

## 요약

- **무슨 일**: 2026-09-16 14:40:52~14:51:47 (KST) 약 11분간 API가 5xx 186건(500·502·504)을 냈다. 학급 리포트뿐 아니라 `/api/units`, `/api/items`도 실패했다.
- **직접 원인**: `ReportService.buildClassReport`가 `@Transactional` 안에서 커넥션을 15.5~21.5초(중앙값 18.9초) 쥐었고, 14:38부터 리포트 요청이 몰려 크기 5의 풀이 고갈됐다.
- **배경 원인**: 풀이 작고(5개, 대기 3초) 리포트 호출에 동시성 제한 · 재시도 완화가 없다. 외부 서명 루프가 트랜잭션 안에 있다(코드 TODO로 이미 알려진 상태).
- **확신 수준**: 풀 고갈 메커니즘은 높음(로그 직접 확인). 점유 시간 15~21초가 서명 루프 탓인지 DB 지연 탓인지는 **미확정**(9절).
- **복구**: 14:52에 평소로 돌아왔다. 왜 멈췄는지는 로그로 알 수 없다.

## 1. 수집 범위

### 1.1 파일별 기록 기간 · 줄 수 · 시각 형식

| 파일 | 줄 수 | 기록 기간 (KST) | 시각 형식 | 타임존 |
|---|---:|---|---|---|
| `app.log` | 5,371 | 09-16 06:43:56 ~ 15:29:51 | `2026-09-16T06:43:56.753+09:00` (ISO-8601, ms) | 오프셋 표기 `+09:00` = KST |
| `nginx-access.log` | 21,086 | 09-16 06:43:56 ~ 15:29:59 | `[16/Sep/2026:06:43:56 +0900]` | 오프셋 표기 `+0900` = KST |
| `nginx-error.log` | 168 | 09-16 09:12:03 ~ 14:51:47 | `2026/09/16 09:12:03` | 표기 없음. 아래 변환 규칙 참고 |
| `mariadb-slow.log` | 21 (항목 3건) | 09-16 09:14:07 ~ 14:37:42 (쿼리 시작 기준) | 헤더 `# Time: 260916  5:37:52`, 본문 `SET timestamp=1789537062;` (epoch) | 헤더는 **UTC** |

### 1.2 KST 변환 규칙

- `app.log`, `nginx-access.log`: 변환 불필요. 이미 KST다.
- `nginx-error.log`: 타임존 표기는 없다. 다만 마지막 오류(14:51:47)가 access log의 마지막 502/504(14:51:47)와 초 단위까지 같으므로 KST로 본다. 변환 불필요.
- `mariadb-slow.log`: `# Time:` 헤더는 **UTC**이므로 **+9시간**을 더한다.
  - 근거: 항목 3의 헤더 `5:37:52`는 `SET timestamp=1789537062`(= 05:37:42 UTC = 14:37:42 KST)보다 10초 뒤다. 헤더는 기록 시각이고 epoch는 쿼리 시작 시각이다. 헤더를 KST로 읽으면 이 관계가 성립하지 않는다.
  - epoch(`SET timestamp`)는 타임존과 무관한 절대 시각이다. `date -r <epoch>`로 KST 변환한다. 쿼리 시작 시각은 epoch 기준으로 쓴다.
  - 항목별 변환 (쿼리 시작 기준, KST): 09:14:07 / 11:02:40 / 14:37:42

| 헤더 (UTC) | epoch | KST |
|---|---|---|
| 0:14:09 | 1789517647 | 09:14:07 |
| 2:02:42 | 1789524160 | 11:02:40 |
| 5:37:52 | 1789537062 | 14:37:42 |

### 1.3 분 단위 이상 징후 집계

"이상"은 `app.log`의 WARN/ERROR, access log의 5xx, `nginx-error.log`의 `[error]`/`[warn]`으로 센다. 모든 시각은 KST다.

**평소 수준 (14:36 이전)**

- `app.log`: WARN/ERROR는 하루 16건, 분당 최대 1건이다. 전부 `state conflict: 마감된 과제는 재배포할 수 없습니다`(409 변환 WARN)이다. 20~30분에 1건 정도로 흩어져 있다.
- access log: 5xx는 0건이다. 14:40 이전 18,883줄 중 5xx가 없다. 404(1,250건)와 400(25건), 409(20건)는 하루 종일 흩어져 있어 평소 노이즈로 본다.
- `nginx-error.log`: 09:12와 13:05에 `[warn]` 응답 버퍼링이 각 1건 있다.
- 트래픽: 분당 요청 수는 14:30~14:59 사이 34~58건이고 장애 구간 전후로 뚜렷한 차이가 없다.

**이상 구간 (분 단위, 건수)**

| 분 (KST) | app WARN/ERROR | access 5xx | nginx-error |
|---|---:|---:|---:|
| 14:33 | 1 (409 WARN, 평소) | 0 | 0 |
| 14:37 | 1 (Hikari leak WARN) | 0 | 0 |
| 14:38 | 2 | 0 | 0 |
| 14:39 | 3 | 0 | 0 |
| 14:40 | 17 | 1 | 0 |
| 14:41 | 31 | 9 | 3 |
| 14:42 | 17 | 20 | 20 |
| 14:43 | 44 | 17 | 8 |
| 14:44 | 17 | 13 | 15 |
| 14:45 | 33 | 27 | 22 |
| 14:46 | 24 | 26 | 25 |
| 14:47 | 24 | 14 | 10 |
| 14:48 | 22 | 15 | 15 |
| 14:49 | 33 | 23 | 20 |
| 14:50 | 12 | 18 | 22 |
| 14:51 | 4 | 3 | 3 |
| 14:52 | 1 (409 WARN, 평소) | 0 | 0 |

- access 5xx 종류: 500 49건, 502 82건, 504 55건. 합계 186건.
- 전체 5xx는 14:40:52에 시작하고 14:51:47에 끝난다. 502/504는 14:41:06~14:51:47이다.
- `app.log`의 `ERROR`는 98건이고 모두 14:40 이후다. 이 중 `Connection is not available, request timed out after 3000ms`(Hikari)는 14:40:52에 처음 나오고 14:49:51에 마지막으로 나온다.
- 14:37:52부터 `ProxyLeakTask`(Connection leak detection) WARN이 나온다. ERROR/5xx보다 약 3분 앞선다. 마지막 `ProxyLeakTask` 관련 줄은 14:51:49다.
- 14:52 이후 `app.log`에는 평소와 같은 409 WARN만 남는다.
- slow log 항목 3건 중 마지막(14:37:42 시작)이 `ProxyLeakTask` 첫 WARN(14:37:52)과 시간상 맞닿아 있다. 관련성은 2단계 이후에 따진다.

**제안하는 이상 구간**

| | 시각 (KST) | 근거 |
|---|---|---|
| 시작 | **14:37:42** | slow log 마지막 항목 시작, `ProxyLeakTask` 첫 WARN 14:37:52. 평소에 없던 신호의 가장 이른 시각 |
| 끝 | **14:52:00** | 마지막 5xx 14:51:47, 마지막 이상 신호 14:51:49. 14:52부터 평소 수준으로 복귀 |
| 사용자 영향 구간 | 14:40:52 ~ 14:51:47 | 첫 ERROR/5xx ~ 마지막 5xx |

### 1.4 앞뒤 여유 제안

앞쪽을 넉넉히 잡는다. 원인은 영향이 보이기 전에 만들어졌을 가능성이 크고, 이 장애는 첫 신호(14:37:42)와 첫 5xx(14:40:52) 사이에 이미 3분 넘게 간격이 있었기 때문이다.

| | 여유 | 수집 구간 (KST) | 이유 |
|---|---|---|---|
| 앞 | **30분** | 14:07:42 | 평소 수준(409 WARN만 흩어짐)을 대조군으로 확보한다. 14:14, 14:33의 평소 WARN도 포함된다 |
| 뒤 | **10분** | 14:52:00 → 15:02 | 복귀 확인용이다. 14:52 이후 평소 수준이 유지되는지만 보면 된다 |

**권장 수집 범위: 2026-09-16 14:07 ~ 15:02 (KST)**

- `app.log`, `nginx-access.log`, `nginx-error.log`: 위 구간으로 자른다.
- `mariadb-slow.log`: 21줄뿐이므로 구간 없이 전체를 쓴다. 항목 3건 중 14:37:42 항목만 이 구간에 들어오고, 나머지 2건(09:14, 11:02)은 그날 평소의 느린 쿼리 수준을 보는 참고용이다.

### 1.5 한계 · 주의

- access log에 응답 시간 필드가 없다. 느려진 시점을 응답 시간으로 볼 수 없고 5xx와 `nginx-error.log`의 upstream timed out만으로 판단한다.
- `app.log`에는 풀 사용률 메트릭 로그가 없다. 풀 상태(`total/active/idle/waiting`)는 ERROR 메시지에만 찍힌다. 14:40:52 이전의 풀 상태는 로그로 알 수 없다.
- `app.log`의 줄 수(5,371) 중 3,444줄은 타임스탬프가 없는 스택 트레이스 연속 줄이다. 줄 수가 아니라 타임스탬프 줄(`^2026-09-16T`) 기준으로 집계했다.
- slow log가 3건뿐이라 임계값(`long_query_time`) 설정을 알아야 누락 여부를 판단할 수 있다. 확인 필요.
- 로그에 `STU-…` 학생 식별자와 Authorization 토큰이 있다. 이후 단계에서 문서에 인용할 때는 마스킹한다.

## 2. 타임라인

범위: 14:07~15:02 KST. 출처는 `incident-logs/a-connection-pool/` 기준 `파일:줄번호`다. API-1·API-2는 nginx upstream 2대의 별칭이다. `app.log`는 호스트 표기가 없으나 slow log 접속원이 API-1뿐이라 API-1 로그로 본다(가정).

| 시각 | 출처 | 사건 | 원문 발췌 |
|---|---|---|---|
| 14:07~14:37 | `nginx-access.log:17405~` | 평소: 총 44.9건/분, 리포트 0.17건/분 | (집계) |
| 14:33:45 | `app.log:1229` | 평소 409 WARN(이상 아님) | `state conflict: 마감된 과제는 재배포할 수 없습니다: distributionId=2` |
| 14:37:42 | `mariadb-slow.log:15-20` | 느린 쿼리 시작(9.8초, 128만 행). 헤더 UTC 5:37:52 | `Query_time: 9.812417 … Rows_examined: 1284310` |
| 14:37:52.199 | `app.log:1243` | 첫 커넥션 누수 경고(리포트 호출 스택) | `Connection leak detection triggered … on thread http-nio-8080-exec-6` |
| 14:38:01 | `app.log:1258-1259`, `nginx-access.log:18801` | 첫 리포트 완료·커넥션 반납(점유 약 19.7초) (선후 불확실) | `built class report for class 3: 3 distributions, 10 submissions` |
| 14:38~14:40 | `app.log:1261~1521` | 누수 경고 반복(14:38 2건, 14:39 3건). 리포트 요청 분당 3→10건 | `Connection leak detection triggered … on thread http-nio-8080-exec-3` |
| 14:40:52.319 | `app.log:1522` | **첫 풀 고갈 ERROR**. 이후 14:49:51까지 36+13건 | `Connection is not available, request timed out after 3000ms (total=5, active=5` |
| 14:40:52 | `app.log:1523`, `nginx-access.log:18918` | 첫 500(`/api/units/M6-2/items`) | `unhandled exception on /api/units/M6-2/items` |
| 14:41:06 | `nginx-error.log:6`, `nginx-access.log:18933` | 첫 504(리포트). 이후 55건 | `upstream timed out (110: Connection timed out) while reading response header` |
| 14:42:01 | `nginx-error.log:10` | API-1 임시 제외 첫 건. 이후 26건 | `upstream server temporarily disabled while reading response header from upstream` |
| 14:42:01 | `nginx-error.log:11`, `nginx-access.log:18970` | API-2 연결 거부 첫 건과 첫 502. API-2 거부 14건 (선후 불확실) | `connect() failed (111: Connection refused) while connecting to upstream` |
| 14:42:04 | `nginx-error.log:12` | `no live upstreams` 첫 건. 이후 68건(502의 나머지) | `no live upstreams while connecting to upstream` |
| 14:43~14:49 | `app.log:1690~4981` | 정점: app WARN/ERROR 14:43 44건, 5xx 14:45 27건, nginx-error 14:46 25건 | (집계) |
| 14:49:51 | `app.log:4980-4981` | 마지막 풀 고갈 ERROR | `Connection is not available, request timed out after 3000ms` |
| 14:50:52 | `nginx-error.log:158-159` | 마지막 API-1 제외 · API-2 거부 | `connect() failed (111: Connection refused)` |
| 14:51:02 | `nginx-error.log:166` | 마지막 `no live upstreams` | `no live upstreams while connecting to upstream` |
| 14:51:37~49 | `app.log:5251`, `app.log:5267` | 마지막 누수 경고, 마지막 반납 | `Previously reported leaked connection … was returned to the pool` |
| 14:51:47 | `nginx-error.log:168`, `nginx-access.log:19381` | 마지막 504, 마지막 5xx | `upstream timed out (110: Connection timed out)` |
| 14:52:09 | `app.log:5269` | 평소 409 WARN만 남음 | `state conflict: 마감된 과제는 재배포할 수 없습니다: distributionId=4` |

14:37:42~14:37:52 사이의 slow log와 첫 누수 경고는 시각 차이가 10초 이내이고 둘 사이를 잇는 요청 ID가 없다.

## 3. 가설과 검증

| # | 가설 | 지지 근거 | 반증 조건 | 확인 방법 · 결과 | 판정 |
|---|---|---|---|---|---|
| H1 | (코드) 리포트 트랜잭션이 커넥션을 오래 쥐어 풀이 고갈됐다 | `app.log:1243-1259`, `ReportService.java:41,69-71` | 누수 경고 스택에 리포트 호출이 없거나 점유가 10초 미만이다 | 누수 스택 134건 중 `classReport` 포함 134건. 점유 15.5~21.5초(중앙값 18.9) | **유지** |
| H2 | (DB) 느린 쿼리·인덱스 부재가 점유 시간을 늘렸다 | `mariadb-slow.log:15-20` | 장애 구간에 느린 쿼리가 다수가 아니다 | 구간 내 slow log 1건(9.8초). 점유 134건(15~21초)을 설명하지 못함 | **주원인은 기각**, 14:37:42 건은 트리거 여부 판단 불가 |
| H3 | (트래픽) 전체 요청 급증이다 | 14:38 이후 5xx 증가 | 분당 총 요청이 평소와 같다 | 총 요청 평소 44.9 vs 장애 43.1/분, 비리포트 44.7 vs 32.4/분 | **기각**. 리포트만 0.17→10.69/분(약 63배) |
| H4 | (배포·설정) 재기동이나 설정 변경이 있었다 | 없음 | 장애 직전 시작·설정 로드 로그가 없다 | `app.log` 시작·설정 로드 줄 0건. 배포 이력 파일 없음 | **판단 불가(로그 부족)** |
| H5 | (인프라) API-2 단독 장애가 부하를 API-1로 몰았다 | `nginx-error.log:11` | API-2 거부가 API-1 포화보다 늦게 시작한다 | 첫 거부 14:42:01 > 포화 14:40:52 > 첫 누수 14:37:52 | 트리거로는 **기각**, 502 증폭 요인으로 유지. 거부 이유는 판단 불가 |

### 판정

채택: **H1**. 코드가 지지한다(`ReportService.java:41` `@Transactional`, `69-71` TODO와 `sign` 호출. `application.yml:14-15` 풀 5개·대기 3초). 확신 수준은 **중간~높음**이다. 풀 고갈 경로는 로그와 코드로 확인했지만, 점유 15~21초의 구성은 확정하지 못했다.
기각: H2(주원인), H3, H5(트리거). 판단 불가: H4. 분석 중 새로 확인된 사실은 502의 정체다. 502 82건은 API-2 연결 거부 14건과 `no live upstreams` 68건이다(앱 오류가 아니라 nginx 단계의 실패).

## 4. 원인

**직접 원인** (인과 순서)

1. 14:37:42경 학급 리포트 요청이 평소(시간당 2~11건)와 달리 몰리기 시작했다. 14시대 179건, 분당 14:38 3건 → 14:40 10건 → 14:41 16건이다. 접속 로그는 완료 시각이라 첫 건(14:38:01, 점유 19.7초)의 시작은 14:37:42경이다. 4개 교사 단말(교사단말A·B·C·D)이 보냈다.
2. `buildClassReport`는 `@Transactional` 하나로 DB 조회부터 서명(`sign`, SHA-256 20만 회)까지 감싼다. DB 작업이 끝나도 서명이 끝날 때까지 커넥션을 반납하지 않는다.
3. 리포트 134건이 모두 `leak-detection-threshold`(10초)를 넘겨 점유했다. 점유 시간은 15.5~21.5초, 중앙값 18.9초다.
4. 분당 10~16건 × 약 19초 ≈ 동시 3~5개(리틀의 법칙, 추정)다. 한 요청이 풀 5개 중 하나를 쥐므로 다른 요청이 섞이면 5개를 넘는다. 14:40:52에 `total=5, active=5, idle=0, waiting=4`가 처음 찍혔다.
5. 풀이 가득 찬 뒤에는 짧은 조회(`/api/units`, `/api/items`)도 3초를 기다리다 `CannotCreateTransactionException` → 500이 됐다. 리포트는 응답이 늦어 nginx가 504로 끊었다.
6. API-1이 timeout으로 nginx에서 임시 제외(26건)되고 API-2는 연결을 거부(14건)해 사용할 수 있는 upstream이 없어졌다(`no live upstreams` 68건). 이 14+68건이 502 82건이다.

**배경 원인**

- 풀이 5개, 대기 3초로 작다. 이 값은 의도된 운영 값이다(`application.yml` 주석, 변경 금지).
- 트랜잭션 안의 긴 작업은 코드에 TODO로 남아 있었지만 해소되지 않았다.
- 리포트 엔드포인트에 동시 실행 제한, 결과 캐시, 클라이언트 재시도 간격 제어가 없다. 느려진 요청이 재시도를 불러 부하를 더했다(추정).
- upstream이 2대인데 API-1 포화 시 API-2가 받아 주지 못했다(연결 거부, 이유 불명). 한 대의 포화가 서비스 전체 502로 번졌다.
- `leak-detection-threshold` 경고는 14:37:52부터 나왔지만 알림으로 이어지지 않았다.

## 5. 근거 로그 줄

| # | 서술 | 파일:줄 | 발췌 |
|---|---|---|---|
| 1 | 리포트 요청이 14:38부터 몰렸다 | `nginx-access.log:18801` | `[16/Sep/2026:14:38:01 +0900] "GET /api/classes/3/report … 200` |
| 1 | 평소 리포트는 시간당 한 자릿수~11건 | `nginx-access.log` 집계 | 시간별 건수 `07:2 08:5 09:2 10:11 11:7 12:11 13:8 14:179` |
| 2 | 트랜잭션 안에 서명이 있다 | `ReportService.java:41, 69-71` | `@Transactional` / `// TODO: 트랜잭션 밖으로 …커넥션(트랜잭션)을 쥔 채로 돈다` / `sign(payload.toString())` |
| 2 | 서명은 SHA-256 20만 회 | `ReportService.java:23` | `SIGNATURE_ROUNDS = 200_000` |
| 3 | 10초 넘게 쥔 커넥션이 리포트 호출에서 생김 | `app.log:1243-1259` | `Connection leak detection triggered … on thread http-nio-8080-exec-6` / 스택에 `ReportService…buildClassReport`·`ReportController.classReport` / `14:38:01.902 … was returned to the pool` |
| 3 | 점유 15.5~21.5초 | `app.log` 쌍 134건 | 경고 시각 + 10초 → 반납 시각 계산. 첫 건 19.7초(1243→1259), 마지막 21.5초(5251→5267) |
| 3 | 임계값 10초 | `application.yml:18` | `leak-detection-threshold: 10000` |
| 4 | 풀 5개가 모두 사용 중 | `app.log:1522` | `14:40:52.319 ERROR … Connection is not available, request timed out after 3000ms (total=5, active=5, idle=0, waiting=4)` |
| 4 | 풀 크기 · 대기 시간 | `application.yml:14-15` | `maximum-pool-size: 5` / `connection-timeout: 3000` |
| 5 | 짧은 조회도 실패 | `nginx-access.log:18918` | `14:40:52 "GET /api/units/M6-2/items" 500` |
| 5 | 500 원인은 트랜잭션 시작 실패 | `app.log:1523` | `unhandled exception on /api/units/M6-2/items` / `CannotCreateTransactionException: Could not open JPA EntityManager` |
| 5 | 리포트는 nginx가 끊음 | `nginx-error.log:6` | `14:41:06 [error] upstream timed out (110: Connection timed out) … GET /api/classes/3/report` |
| 5 | 504 첫 건 · 마지막 건 | `nginx-access.log:18933, 19381` | `14:41:06 … 504` / `14:51:47 … 504` |
| 6 | API-1 제외 · API-2 거부 · upstream 없음 | `nginx-error.log:10-12` | `upstream server temporarily disabled` / `connect() failed (111: Connection refused)` / `no live upstreams` |
| 6 | 502 = 거부 14 + 없음 68 | `nginx-error.log` 집계 | `Connection refused` 14건, `no live upstreams` 68건, 502 82건 |
| 5 | 5xx 중 리포트 92건, 나머지는 다른 API | `nginx-access.log` 집계 | report 41+17+34, 그 외 94건(`/api/units` 26, `/api/items/*`, `/api/distributions/*` 등) |
| 복구 | 14:52 이후 평소 수준 | `app.log:5269` | `14:52:09 WARN … state conflict: 마감된 과제는…`(평소 409 WARN만 남음) |

## 6. 수정안 (지금 적용, 코드 변경은 하지 않고 제안만)

대상: `modern/api/src/main/java/com/example/assignment/ReportService.java`

- **서명을 트랜잭션 밖으로 뺀다.** 현재 `buildClassReport`(41행 `@Transactional`)를 둘로 나눈다.
  1. DB 조회·집계만 하는 `@Transactional(readOnly = true)` 메서드. 반환은 서명 대상 payload와 집계값을 담은 record.
  2. 트랜잭션 없는 `buildClassReport`가 1을 호출한 뒤 `sign(payload)`를 호출해 `ClassReport`를 조립한다.
- 같은 클래스 안에서 `this.`로 호출하면 프록시를 거치지 않아 트랜잭션이 걸리지 않는다. 조회 메서드는 별도 빈(예: `ReportQueryService`, 같은 `assignment` 패키지)으로 둔다.
- 효과: 커넥션 점유가 DB 조회 시간으로 줄어든다. 서명이 15초 걸려도 풀을 쥐지 않는다.
- 테스트: `ReportServiceTest`에 서명 호출 시점에 트랜잭션이 활성이 아님을 검증하는 케이스를 추가한다. 기존 응답 값이 바뀌지 않는지는 `./gradlew test`와 `characterization`의 `npm test`로 확인한다.
- 풀 설정(`maximum-pool-size` 등)은 올리지 않는다. 풀을 늘리면 증상만 미루고, CLAUDE.md도 변경을 금지한다.

## 7. 재발 방지 (구조적 장치)

- **자동 검사**: `@Transactional` 메서드 안의 반복·외부 호출을 막는 규칙을 코드 리뷰 체크리스트나 아키텍처 테스트에 넣는다. `TODO`로 남은 트랜잭션 위반이 릴리스를 통과하지 못하게 한다.
- **부하 보호**: 리포트 엔드포인트에 동시 실행 상한(세마포어/벌크헤드)을 둔다. 상한을 넘으면 3초 대기 없이 즉시 429를 반환해 다른 API가 풀을 쓸 수 있게 한다.
- **결과 재사용**: 같은 학급 리포트를 짧은 시간 안에 다시 계산하지 않도록 캐시(TTL)를 둔다. 의존성 추가가 필요하면 먼저 묻는다.
- **클라이언트**: 504 후 재시도에 백오프를 적용하고 중복 요청을 막는다(LMS 프런트 측 협의).
- **느린 쿼리**: `submission`에서 `distribution_id=5` 조회가 128만 행을 훑었다(`mariadb-slow.log:15-20`). `EXPLAIN`으로 인덱스를 확인하고, 필요하면 스키마 변경 요청으로 올린다(승인 필요).
- **부하 시험**: 리포트 동시 10건 시나리오를 배포 전 점검 항목에 넣는다.

## 8. 모니터링 항목

| 지표 | 제안 임계값 | 이번 타임라인에서 경보 시각 |
|---|---|---|
| Hikari 사용 중 커넥션 비율 (`active/max`) | 80%(4/5) 이상 1분 지속 | 풀 지표가 로그에 없어 확인 불가. 14:40경 포화(추정) |
| Hikari 대기 스레드 수 (`waiting`) | 1 이상 30초 지속 | 14:40:52 (첫 `waiting=4`) |
| `ProxyLeakTask` 경고 | 10분에 1건 이상 | **14:37:52** (가장 빠름, 5xx보다 약 3분 앞) |
| 커넥션 획득 대기 시간 p95 | 500ms 초과 | 지표 없음. 대기가 3000ms에 닿은 시각은 14:40:52 |
| 엔드포인트별 응답 시간 p95 (리포트) | 5초 초과 | access log에 응답 시간이 없어 확인 불가. 첫 느린 응답 완료는 14:38:01 |
| 5xx 비율 | 1분 5xx 3건 또는 1% 초과 | 14:41 (9건, 약 20%) |
| `nginx upstream timed out` | 1분에 1건 이상 | 14:41:06 |

이번 사례에서는 누수 경고 알림이 가장 먼저(14:37:52) 울릴 수 있었고, 사용자 영향(14:40:52)보다 약 3분을 번다.

## 9. 확인하지 못한 것

- **점유 시간 15~21초의 구성**: DB 조회 시간과 서명 시간을 나눠 볼 로그가 없다. 코드상 DB 외 작업은 서명뿐이지만, 20만 회 해시가 보통 이 정도로 오래 걸리는지는 실행해 보지 않았다. CPU 경합이나 DB 지연이 섞였을 수 있다.
- **14:37:42의 느린 쿼리(9.8초, 128만 행)와 리포트의 관계**: 같은 시각에 시작했지만 어떤 요청이 낸 쿼리인지 연결하는 키(스레드·요청 ID)가 없다. 이 쿼리는 slow log에 한 건뿐이다.
- **리포트 요청이 14:38에 몰린 이유**: 단말 4곳의 사용 패턴(자동 새로고침, 재시도, 수업 종료 시각)을 알 수 없다.
- **API-2가 연결을 거부한 이유**: 14:42:01~14:50:52에 14건 거부했다. API-2의 앱 로그와 프로세스 상태가 없다. nginx `max_fails`·`fail_timeout`·`proxy_read_timeout` 설정값도 모른다.
- **14:52에 멈춘 이유**: 트래픽 감소인지 재시작인지 로그가 없다.
- **14:40:52 이전 풀 상태**: 풀 메트릭이 없다.
- **`long_query_time`**: slow log 3건이 전부인지 알 수 없다.

**다음을 위해 남길 로그**: 요청 ID(MDC)를 app·nginx에 공통으로, nginx에 `$request_time`·`$upstream_response_time`, Hikari 풀 상태(active/idle/waiting) 주기 로그 또는 메트릭, `ReportService`의 단계별 소요 시간(조회·서명) 로그, slow log 임계값 낮춤.

마스킹 적용: IP 4건(역할 별칭 교사단말A~D) · 이메일 0건 · 학생 식별자 0건 · 토큰 0건 · 비밀번호·접속 문자열 0건
