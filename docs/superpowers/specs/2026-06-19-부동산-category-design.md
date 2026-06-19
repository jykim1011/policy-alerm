# 부동산 구독 카테고리 추가 설계

**날짜:** 2026-06-19  
**범위:** 설정 > 구독 카테고리에 "부동산 전체" 항목 추가

---

## 배경

현재 앱에서 부동산 관련 정책은 청약 / 대출 / 세금 / 재개발 / 전월세 5개 서브카테고리로 분리돼 있다. 사용자가 부동산 전체를 받으려면 5개를 개별로 구독해야 하고, subcategory가 "부동산"인 fallback 기사(어느 서브에도 해당 안 됨)는 어느 탭에서도 보이지 않는다.

이를 해결하기 위해 **"부동산"** 을 독립 구독 카테고리로 추가한다.

---

## 결정 사항

- 부동산 구독 = `category == "부동산"` 인 모든 기사 (5개 서브 + fallback 포함)
- 기존 청약 / 대출 / 세금 / 재개발 / 전월세 개별 구독은 그대로 유지
- 사용자는 "부동산 전체"를 켜거나, 개별 서브카테고리를 켜거나, 둘 다 켤 수 있다

---

## 파이프라인 분류 로직 (변경 없음)

**1차 분류 (`crawler.py`)** — 제목에 아래 키워드 중 하나라도 포함되면 `category = "부동산"`

```
부동산, 주택, 청약, 분양, 전세, 월세, 임대주택, 임대차,
재개발, 재건축, 정비사업, LTV, DSR, 종부세, 양도세, 취득세,
주담대, 분양가, 공시가격, 그린벨트, 신도시, 택지, 주거,
아파트, 주택담보, 다주택, 1주택, 보금자리, 공공주택
```

제외: `전세계, 인재개발, 씨닭, 닭 분양`

**2차 분류 (`main.py`)** — category가 "부동산"인 경우 subcategory 세분화

| 제목 키워드 | subcategory |
|---|---|
| 청약 / 분양 / 공급 | 청약 |
| 대출 / LTV / DSR / 금리 | 대출 |
| 세금 / 취득세 / 종부세 / 양도세 | 세금 |
| 재개발 / 재건축 / 정비 | 재개발 |
| 전세 / 월세 / 임대 | 전월세 |
| 위 어디도 해당 없음 | 부동산 (fallback) |

---

## 변경 범위

### 1. `CategoryMeta.kt`

`CATEGORY_LIST`에 `부동산` 항목 추가. 위치: "전체" 바로 뒤, "청약" 앞.

```kotlin
CategoryMeta("부동산", "🏠", "부동산 전체"),
```

`SUBSCRIBABLE_CATEGORIES = CATEGORY_LIST.drop(1)` 이므로 설정 화면 토글과 홈 탭에 자동 반영된다.

### 2. `HomeViewModel.kt`

`HomeUiState.policies` getter의 필터 조건 변경.

**변경 전**
```kotlin
else -> allPolicies.filter { it.subcategory == selectedCategory }
```

**변경 후**
```kotlin
else -> if (selectedCategory == "부동산")
    allPolicies.filter { it.category == "부동산" }
else
    allPolicies.filter { it.subcategory == selectedCategory }
```

### 3. `functions/index.js`

Firestore 구독 쿼리를 `array-contains-any` 로 교체한다.

**변경 전**
```js
.where("subscribed_categories", "array-contains", policy.subcategory)
```

**변경 후**
```js
const matchValues = [...new Set([policy.subcategory, policy.category])];
// 예: 청약 기사 → ["청약", "부동산"]
// 예: fallback 기사 → ["부동산"]   (dedup으로 중복 제거)

const usersSnap = await db
  .collection("users")
  .where("subscribed_categories", "array-contains-any", matchValues)
  .get();
```

`Set` dedup이 필요한 이유: fallback 기사는 `subcategory == category == "부동산"` 이므로 중복값이 생기며, Firestore `array-contains-any`는 중복값 입력 시 에러를 낸다.

---

## 배포 순서

1. **Cloud Function 먼저 배포** (`firebase deploy --only functions`)
   - 기존 사용자: 아무도 `subscribed_categories`에 "부동산"이 없으므로 동작 변화 없음
   - 신규 사용자: 앱이 아직 변경 전이므로 역시 영향 없음

2. **Android 앱 업데이트** (Play Store)
   - 이후 사용자가 "부동산 전체" 구독 시 Cloud Function이 정상 처리

---

## 영향 범위

| 구독 상태 | 알림 수신 기사 |
|---|---|
| 청약만 구독 | subcategory = 청약인 기사만 |
| 부동산만 구독 | category = 부동산인 모든 기사 (청약+대출+세금+재개발+전월세+fallback) |
| 청약 + 부동산 둘 다 구독 | category = 부동산인 모든 기사 (중복 알림 없음 — Firestore가 같은 문서 한 번만 매칭) |

---

## 변경하지 않는 것

- `SettingsViewModel.kt` — 카테고리 토글 로직이 범용적이므로 수정 불필요
- `SettingsScreen.kt` — `SUBSCRIBABLE_CATEGORIES` 순회 방식 그대로
- 파이프라인 (`crawler.py`, `main.py`, `notifier.py`) — 분류 로직 변경 없음
- 홈 탭 "전체" 동작 — 변경 없음
