# 문항 은행 비즈니스 규칙 교차 점검

대상은 문항 검색, 등록 및 목록 조회 코드로 한정했다. `docs/`의 기존 파일과 외부 라이브러리 코드는 읽지 않았다.

수정 처리(`UPDATE`) 코드는 대상 애플리케이션 파일에 없으므로, 수정 시 검증 규칙은 추출하지 않았다.

## 검색 조건 조합

### CX-01 — 단원·난이도·태그가 함께 주어지면 각 조건은 모두 충족해야 검색된다.

- 근거: [search.php:118-120](../../search.php#L118-L120), [search.php:215-220](../../search.php#L215-L220), [search.php:244-247](../../search.php#L244-L247)
- 근거 코드:

```php
$where .= " AND unit_code = ?";
$where .= " AND level = ?";
$where .= " AND EXISTS (SELECT 1 FROM item_tag it JOIN tag t ON t.id = it.tag_id"
```

### CX-02 — 단원 조건은 입력 단원 코드와 문항의 `unit_code`가 정확히 같은 문항만 남긴다.

- 근거: [search.php:109-120](../../search.php#L109-L120)
- 근거 코드:

```php
$unit = trim($unit);
if ($unit !== '') {
    $where .= " AND unit_code = ?";
```

### CX-03 — 형식에 맞지 않거나 소문자를 포함한 단원 코드도 정규화·차단하지 않고 원문 그대로 검색 조건에 사용한다.

- 근거: [search.php:111-120](../../search.php#L111-L120)
- 근거 코드:

```php
if (!preg_match('/^[A-Za-z][0-9]{1,2}-[0-9]{1,2}$/', $unit)) {
    $warnings[] = '단원 코드 형식이 올바르지 않습니다. (예: M5-1)';
}
$values[] = $unit;
```

### CX-04 — 난이도를 비우면 전체 난이도가 아니라 `level` 5를 제외한 문항만 검색한다.

- 근거: [search.php:211-216](../../search.php#L211-L216)
- 근거 코드:

```php
$level = trim($level);
if ($level == '') {
    $where .= " AND level < 5";
}
```

### CX-05 — 난이도 값이 한 자리 1~5이면 그 난이도와 정확히 일치하는 문항만 검색한다.

- 근거: [search.php:217-221](../../search.php#L217-L221)
- 근거 코드:

```php
else if (preg_match('/^[1-5]$/', $level)) {
    $where .= " AND level = ?";
    $values[] = (int)$level;
}
```

### CX-06 — 1~5 형식 밖의 난이도는 경고만 내고 정수 변환한 값으로 계속 일치 검색한다.

- 근거: [search.php:223-229](../../search.php#L223-L229)
- 근거 코드:

```php
$warnings[] = '난이도는 1~5 사이여야 합니다.';
$where .= " AND level = ?";
$values[] = (int)$level;
```

### CX-07 — 태그 조건은 해당 이름의 태그가 연결된 문항만 남기며, 부분 일치가 아닌 이름 일치로 판정한다.

- 근거: [search.php:235-247](../../search.php#L235-L247)
- 근거 코드:

```php
$where .= " AND EXISTS (SELECT 1 FROM item_tag it JOIN tag t ON t.id = it.tag_id"
        . " WHERE it.item_id = v_item_public.id AND t.name = ?)";
$values[] = $tag;
```

### CX-08 — 50자를 넘는 태그 검색어는 앞 50자만 조건에 사용한다.

- 근거: [search.php:240-247](../../search.php#L240-L247)
- 근거 코드:

```php
if (mb_strlen($tag, 'UTF-8') > 50) {
    $tag = mb_substr($tag, 0, 50, 'UTF-8');
}
```

## 등록 검증 및 저장

### CX-09 — 등록 제목은 앞뒤 공백을 제거한 뒤 5자 이상 200자 이하여야 한다.

- 근거: [register.php:41](../../register.php#L41), [register.php:49-53](../../register.php#L49-L53)
- 근거 코드:

```php
$title  = isset($_POST['title'])  ? trim((string)$_POST['title'])  : '';
if (mb_strlen($title, 'UTF-8') < 5) {
if (mb_strlen($title, 'UTF-8') > 200) {
```

### CX-10 — 등록 지문은 앞뒤 공백을 제거한 뒤 빈 값이면 등록할 수 없다.

- 근거: [register.php:42](../../register.php#L42), [register.php:55-57](../../register.php#L55-L57)
- 근거 코드:

```php
$stem   = isset($_POST['stem'])   ? trim((string)$_POST['stem'])   : '';
if ($stem === '') {
    $errors[] = '지문을 입력해야 합니다.';
}
```

### CX-11 — 등록 단원은 현재 단원 선택 목록에 있는 ID여야 한다.

- 근거: [register.php:27-29](../../register.php#L27-L29), [register.php:60-67](../../register.php#L60-L67)
- 근거 코드:

```php
if ((string)$u['id'] === $unitId) {
    $unitOk = true;
}
if (!$unitOk) {
```

### CX-12 — 등록 난이도는 정확히 한 자리인 1~5여야 한다.

- 근거: [register.php:69-71](../../register.php#L69-L71)
- 근거 코드:

```php
if (!preg_match('/^[1-5]$/', $level)) {
    $errors[] = '난이도는 1~5 사이여야 합니다.';
}
```

### CX-13 — 등록 태그는 현재 태그 선택 목록에 있는 ID만 연결하며, 목록 밖 ID는 등록 오류로 만들지 않고 제외한다.

- 근거: [register.php:73-81](../../register.php#L73-L81), [register.php:83-105](../../register.php#L83-L105)
- 근거 코드:

```php
if ((string)$t['id'] === (string)$tid) {
    $validTagIds[] = (int)$tid;
}
if (!empty($validTagIds)) {
```

### CX-14 — 제목·지문·단원·난이도 검증 오류가 없을 때만 문항과 유효 태그를 저장한다.

- 근거: [register.php:83-105](../../register.php#L83-L105)
- 근거 코드:

```php
if (empty($errors)) {
    $conn->begin_transaction();
    $stmt = $conn->prepare(
```

### CX-15 — 새 문항은 상태 `R`로 저장된다.

- 근거: [register.php:92-98](../../register.php#L92-L98)
- 근거 코드:

```php
"INSERT INTO item (id, unit_id, title, stem, level, status, created_at, updated_at)
 VALUES (?, ?, ?, ?, ?, 'R', NOW(), NOW())"
```

## 목록 기본 정렬 및 제외

### CX-16 — 문항 목록은 `v_item_public` 뷰의 행만 대상으로 하므로, 그 뷰에 포함되지 않은 문항은 목록에서 제외된다.

- 근거: [search.php:519-526](../../search.php#L519-L526)
- 근거 코드:

```php
$from   = " FROM v_item_public";
$sql      = $select . $from . $where . $orderBy . $limit;
$countSql = "SELECT COUNT(*) AS cnt" . $from . $where;
```

### CX-17 — 정렬 기준이 없거나 허용되지 않은 경우 목록은 난이도 내림차순, 같은 난이도에서는 ID 오름차순으로 정렬한다.

- 근거: [search.php:317-325](../../search.php#L317-L325)
- 근거 코드:

```php
case '':
    $orderBy = " ORDER BY level DESC, id ASC";
    break;
```

### CX-18 — 목록은 페이지당 최대 20건만 반환한다.

- 근거: [search.php:333-349](../../search.php#L333-L349), [search.php:521-525](../../search.php#L521-L525)
- 근거 코드:

```php
$offset = ($page - 1) * 20;
$limit  = " LIMIT 20 OFFSET " . (int)$offset;
$sql      = $select . $from . $where . $orderBy . $limit;
```
