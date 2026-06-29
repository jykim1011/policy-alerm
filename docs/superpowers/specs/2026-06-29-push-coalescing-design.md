# 푸시 코얼레싱 — 회차 단위 배치 푸시 설계

작성일: 2026-06-29

## 문제

현재 파이프라인은 30분마다 실행되며, `notify_pending()`이 이번 회차의 새 정책을 루프 돌며
정책당 `new_policies/{id}` 문서를 1건씩 생성한다. Cloud Function `onNewPolicy`가 문서당
1회 트리거되어 **구독자에게 정책당 푸시 1건**을 발송한다.

따라서 한 회차에서 N건이 모이면 구독자는 사용자당 **N개의 푸시**를 받는다. 야간(KST 22~09시)은
이미 카운트만 누적했다가 09:00 `morningDigest`가 묶음 1건으로 발송하지만, 주간에는 묶음 처리가
없다.

목표: 한 회차에 여러 정책이 있어도 **가급적 최소한의 푸시**로 표시한다.

## 결정 사항

- **규모 전제**: 현실적 규모(수천~수십만). 개인화 묶음 카운트를 유지하는 fan-out-on-write 모델을
  그대로 쓴다. (수백만~1,000만 목표라면 FCM Topic 메시징으로 재설계해야 하나, 이번 범위 아님.)
- **푸시 형태(하이브리드)**:
  - 사용자 매칭 **1건** → 기존처럼 상세 푸시(제목 표시, 탭 시 해당 정책).
  - 사용자 매칭 **2건 이상** → 요약 푸시 `"새 정책 N건 (부동산 2·고용 1)"`, 탭 시 알림 탭(history).
- **건수 기준**: 각 사용자가 **구독한 분야에 매칭되는 정책만** 카운트(현 동작과 일관).
- **야간 포맷 통일**: `morningDigest`도 동일 요약 포맷을 쓰도록 분야별 누적을 추가한다.

## 비용 근거 (왜 A가 맞는가)

FCM 발송은 무료이므로 비용은 Firestore 작업에서 발생한다. 접근 A는 구독자 조회를
**정책당 N회 → 회차당 1회**로 줄여 현재보다 모든 비용 축에서 싸다. 알림함(history) 사용자별
기록 비용은 두 방식 공통이며 본 설계의 변경 대상이 아니다.

## 아키텍처

```
파이프라인(30분) ─ notify_pending()
   policy별 CDN 반영 확인 (기존 유지)
   CDN 확인된 정책들을 모아 → new_policy_batches/{runId} 문서 1건 write
        │
        ▼ onDocumentCreated("new_policy_batches/{runId}") — 회차당 1회
onNewPolicyBatch
   1) 배치 정책들의 category·subcategory 합집합으로 구독자 조회(필요 시 30개 청크 분할)
   2) 사용자별: 구독 분야 ∩ 배치 정책 → 매칭 정책 목록/건수 산출 (computeUserDigest)
   3) 알림함 기록: (사용자 × 매칭 정책)마다 notifications/{policyId} 기록 (기존과 동일)
   4) 주간: 사용자별 푸시 1건 발송(sendEach) / 야간: overnight_pending·overnight_breakdown 누적
   5) 배치 문서 삭제
```

## 컴포넌트

### 1. 배치 문서 — `new_policy_batches/{runId}`

```js
{
  run_id: string,                 // 예: "20260629T093000Z_morning"
  batch: "morning" | "evening",   // 기존 라벨 유지
  created_at: serverTimestamp,
  policies: [ { id, category, subcategory, title }, ... ]
}
```

`runId` = `<UTC ISO 압축 타임스탬프>_<batch>`. 회차당 유일.

### 2. 파이프라인 — `pipeline/notifier.py`, `pipeline/main.py`

- `notify_new_policy` → `notify_new_batch(items: list[PolicyItem], batch: str, run_id: str)`로 교체.
  `new_policy_batches/{run_id}`에 정책 배열을 담은 문서 1건을 write.
- `notify_pending()`: 기존 per-policy CDN 반영 확인은 유지하되, 확인된 정책을 리스트에 모아
  루프 종료 후 `notify_new_batch(confirmed, batch, run_id)`를 **한 번** 호출.
  - CDN 타임아웃/실패분은 기존처럼 `pending_notify.json`에 남겨 다음 회차에서 재시도(다음 배치에 합류).
  - 확인된 정책이 0건이면 배치 문서를 쓰지 않는다.
  - 한 회차에 `morning`/`evening` 등 batch 라벨이 섞일 수 있으므로, 배치 문서의 `batch`는
    대표 라벨(해당 run의 `BATCH` 환경값)을 사용한다. 푸시 내용에는 영향 없음(라벨은 분류용).

### 3. Cloud Function — `functions/index.js`

신규 `onNewPolicyBatch` = `onDocumentCreated("new_policy_batches/{runId}")`.

순수 헬퍼로 분리(단위 테스트 대상):
- `computeUserDigest(subscribedCategories, policies)` →
  `{ matched: Policy[], count, breakdown: {category: n} }`.
  매칭 규칙: 정책의 `subcategory` 또는 `category`가 사용자 `subscribed_categories`에 포함되면 매칭
  (현 `onNewPolicy`의 `array-contains-any` 의미와 동일).
- `formatDigestBody(breakdown)` → `"새 정책 N건 (부동산 2·고용 1)"`.
  분야를 건수 desc로 정렬, 상위 3분야 표기, 그 외는 `"외 M건"`으로 합산.

함수 흐름:
1. `policies` 로드. 빈 배열이면 문서 삭제 후 종료.
2. 합집합 `matchValues = unique(category, subcategory)`. `array-contains-any`는 값 30개 제한이므로
   30개씩 청크로 나눠 조회하고 사용자 셋을 doc.id로 머지(중복 제거).
3. 각 사용자에 대해 `computeUserDigest` 실행. `count === 0`이면 스킵.
4. 알림함 기록: 사용자별 매칭 정책마다 `users/{uid}/notifications/{policyId}`를 set
   (title·category·subcategory·received_at·read=false). Firestore 배치 500개 제한 청크.
5. 배치 문서 삭제(중복 트리거 방지 — 현 `onNewPolicy`와 동일 수준).
6. 야간(`isQuietHoursKst()`): 사용자별
   `overnight_pending`(스칼라, FieldValue.increment(count))과
   `overnight_breakdown.{category}`(dotted-path increment) 누적. 푸시 없음. 종료.
7. 주간: 토큰 있는 사용자별 메시지 구성 후 `sendEach`(500 청크).
   - `count === 1` → `notification{ title:"새 {category} 정책", body: 제목 }`,
     `data{ policy_id, category, subcategory, title, body }` (기존 단건 포맷).
   - `count >= 2` → `notification{ title:"정책 알리미", body: formatDigestBody(breakdown) }`,
     `data{ open_tab:"history" }`.
   - 만료 토큰(`registration-token-not-registered`) 정리는 기존 로직 재사용.

### 4. `morningDigest` 포맷 통일

- 누적 필드: 기존 `overnight_pending`(스칼라, `>0` 쿼리용) 유지 + 신규 `overnight_breakdown`(맵).
- 발송 본문: `overnight_breakdown`가 있으면 `formatDigestBody(breakdown)`,
  없으면(구버전 데이터) 기존 `"밤사이 새 정책 {count}건이 도착했어요"`로 폴백.
- 리셋: 발송/실패와 무관하게 `overnight_pending`과 `overnight_breakdown` 둘 다
  `FieldValue.delete()` (기존처럼 중복 방지 우선).

## 데이터 흐름 / 호환성

- 앱(Android)·웹은 변경 불필요. 알림함 문서 스키마와 단건 푸시 `data` 페이로드는 동일.
  요약 푸시는 `data.open_tab="history"`로 기존 morningDigest 묶음과 같은 탭 인텐트를 쓴다.
- `new_policies/{id}` 컬렉션과 기존 `onNewPolicy`는 제거(배치 경로로 대체). 진행 중이던
  `new_policies` 문서가 없는지 배포 전 확인.

## 에러 처리

- 배치 문서 처리 중 throw 시 gen2 at-least-once 재시도 → 중복 발송 가능 창은 현 `onNewPolicy`와
  동일 수준으로 수용(문서 삭제 전 실패 시). 추가 멱등 가드는 YAGNI로 제외.
- `sendEach` 청크가 throw해도 `morningDigest`처럼 다음 청크로 계속 진행하는 것을 기본으로 한다.
- 파이프라인은 CDN 확인 실패분을 pending에 남겨 다음 회차 재시도(기존 보장 유지).

## 테스트

- `pipeline/tests/test_notifier.py`: `notify_new_batch`가 `new_policy_batches/{run_id}`에
  정책 배열을 담은 문서 1건을 write하는지 검증(기존 per-policy 테스트 대체/갱신).
- `functions/`: 현재 JS 테스트 없음 → 순수 헬퍼 단위 테스트 신규 추가.
  - `formatDigestBody`: 0/1/3/5분야, 상위 3 + "외 M건" 경계.
  - `computeUserDigest`: 매칭 0/1/다수, category·subcategory 매칭, breakdown 집계.
  - 테스트 러너(node:test 등) 한 개 도입.

## 범위 밖 (YAGNI)

- FCM Topic 메시징 전환(대규모용). 단, 알림함/구독 구조를 불필요하게 토픽 전환을 막는 방향으로
  바꾸지 않는다.
- OS 알림 그룹핑(group summary). 실제 푸시 수를 줄이지 못해 목표 미달.
- 사용자별 야간 방해금지 시간 커스터마이즈.
