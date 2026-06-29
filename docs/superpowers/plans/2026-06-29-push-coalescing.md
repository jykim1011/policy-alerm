# 푸시 코얼레싱 (회차 단위 배치 푸시) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 한 회차(30분)에 여러 정책이 모여도 사용자당 푸시를 1건으로 묶어, 알림 피로를 줄인다.

**Architecture:** 파이프라인이 정책당 `new_policies/{id}` 문서 N개를 쓰던 것을, 회차 끝에 확인된 정책 전체를 담은 `new_policy_batches/{runId}` 문서 1건으로 바꾼다. 새 Cloud Function `onNewPolicyBatch`가 구독자를 1회 조회하고 사용자별 매칭을 계산해 푸시 1건(1건=상세 / 2~3건=제목 나열 / 4건+=분야 카운트)을 보낸다. 야간은 기존처럼 누적했다가 `morningDigest`가 보낸다.

**Tech Stack:** Python(파이프라인, pytest), Node.js 22 CommonJS(Cloud Functions, firebase-functions v7 gen2, firebase-admin v13), `node --test`.

## Global Constraints

- 알림 `policy_id`는 반드시 문서 본문 `id`(파이프라인 저장)에서 온다 — gen2 `event.params`는 한글 ID를 모지바케로 깨뜨림(firebase-functions#1459).
- 단건 푸시 `data` 페이로드와 알림함(`users/{uid}/notifications/{policyId}`) 문서 스키마는 기존과 동일하게 유지(앱/웹 무변경).
- Firestore 쓰기 배치는 최대 500개 작업, `array-contains-any`/`in`은 값 최대 30개 — 청크 분할.
- 야간 방해금지(KST): `QUIET_START_KST = 22`, `QUIET_END_KST = 9`.
- FCM 채널: `FCM_CHANNEL_ID = "policy_alerts_v2"`.

---

### Task 1: 순수 다이제스트 헬퍼 + 단위 테스트 (`functions/digest.js`)

`onNewPolicyBatch`와 `morningDigest`가 공유할 순수 함수를 먼저 테스트와 함께 만든다. Firestore/FCM 의존이 없어 단위 테스트 가능.

**Files:**
- Create: `functions/digest.js`
- Create: `functions/test/digest.test.js`
- Modify: `functions/package.json` (test 스크립트 추가)

**Interfaces:**
- Produces:
  - `computeUserDigest(subscribedCategories: string[], policies: {id,category,subcategory,title}[]) → { matched: Policy[], count: number, breakdown: {[category]: number} }`
  - `formatBreakdown(breakdown: {[category]: number}) → string` — 예: `"부동산 3·고용 2·외 1건"`
  - `formatDigestBody(matched: Policy[], breakdown) → string` — N≤3 제목 나열, N≥4 `formatBreakdown`

- [ ] **Step 1: test 스크립트 추가** — `functions/package.json`의 최상위에 `"scripts"` 추가:

```json
{
  "name": "policy-alarm-functions",
  "version": "1.0.0",
  "main": "index.js",
  "scripts": {
    "test": "node --test"
  },
  "engines": { "node": "22" },
  "dependencies": {
    "firebase-admin": "^13.0.0",
    "firebase-functions": "^7.0.0"
  }
}
```

- [ ] **Step 2: 실패하는 테스트 작성** — `functions/test/digest.test.js`:

```js
const { test } = require("node:test");
const assert = require("node:assert");
const { computeUserDigest, formatBreakdown, formatDigestBody } = require("../digest");

const P = (id, category, subcategory, title) => ({ id, category, subcategory, title });

test("computeUserDigest: subcategory 또는 category 매칭만 집계", () => {
  const policies = [
    P("a", "부동산", "청약", "청약 개편"),
    P("b", "부동산", "대출", "대출 규제"),
    P("c", "고용", "고용", "고용장려금"),
    P("d", "복지", "복지", "복지 확대"),
  ];
  // 사용자: 청약(subcategory) + 고용(category) 구독
  const r = computeUserDigest(["청약", "고용"], policies);
  assert.strictEqual(r.count, 2);
  assert.deepStrictEqual(r.matched.map((p) => p.id), ["a", "c"]);
  assert.deepStrictEqual(r.breakdown, { 부동산: 1, 고용: 1 });
});

test("computeUserDigest: 매칭 없으면 count 0", () => {
  const r = computeUserDigest(["환경"], [P("a", "부동산", "청약", "x")]);
  assert.strictEqual(r.count, 0);
  assert.deepStrictEqual(r.matched, []);
});

test("computeUserDigest: 구독 목록이 비어/없어도 안전", () => {
  assert.strictEqual(computeUserDigest(undefined, [P("a", "부동산", "청약", "x")]).count, 0);
  assert.strictEqual(computeUserDigest([], [P("a", "부동산", "청약", "x")]).count, 0);
});

test("formatBreakdown: 상위 3분야 + 외 M건", () => {
  assert.strictEqual(formatBreakdown({ 부동산: 3, 고용: 2 }), "부동산 3·고용 2");
  assert.strictEqual(
    formatBreakdown({ 부동산: 3, 고용: 2, 복지: 2, 환경: 1, 교육: 1 }),
    "부동산 3·고용 2·복지 2·외 2건"
  );
});

test("formatDigestBody: N<=3 제목 나열", () => {
  const matched = [P("a", "부동산", "청약", "청약 개편"), P("b", "고용", "고용", "고용장려금 개편")];
  assert.strictEqual(formatDigestBody(matched, { 부동산: 1, 고용: 1 }), "청약 개편 · 고용장려금 개편");
});

test("formatDigestBody: 긴 제목은 24자로 절단 + …", () => {
  const long = "가".repeat(40);
  const matched = [P("a", "부동산", "청약", long)];
  assert.strictEqual(formatDigestBody(matched, { 부동산: 1 }), "가".repeat(24) + "…");
});

test("formatDigestBody: N>=4 분야 카운트", () => {
  const matched = [
    P("a", "부동산", "청약", "t1"),
    P("b", "부동산", "대출", "t2"),
    P("c", "고용", "고용", "t3"),
    P("d", "복지", "복지", "t4"),
  ];
  assert.strictEqual(formatDigestBody(matched, { 부동산: 2, 고용: 1, 복지: 1 }), "부동산 2·고용 1·복지 1");
});
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `cd functions && npm test`
Expected: FAIL — `Cannot find module '../digest'`

- [ ] **Step 4: `functions/digest.js` 구현**

```js
/**
 * 순수 다이제스트 헬퍼 — Firestore/FCM 의존 없이 단위 테스트 가능.
 * onNewPolicyBatch(주간)와 morningDigest(야간)가 공유한다.
 */

/** 제목을 max자 초과 시 잘라 …를 붙인다. */
function truncate(s, max) {
  return s.length > max ? s.slice(0, max) + "…" : s;
}

/**
 * 사용자의 구독 분야와 배치 정책을 교차해 매칭 정책/건수/분야별 집계를 낸다.
 * 매칭 규칙: 정책의 subcategory 또는 category가 구독 목록에 있으면 매칭
 * (현 onNewPolicy의 array-contains-any 의미와 동일).
 */
function computeUserDigest(subscribedCategories, policies) {
  const set = new Set(subscribedCategories || []);
  const matched = policies.filter((p) => set.has(p.subcategory) || set.has(p.category));
  const breakdown = {};
  for (const p of matched) {
    breakdown[p.category] = (breakdown[p.category] || 0) + 1;
  }
  return { matched, count: matched.length, breakdown };
}

/** 분야별 건수를 "부동산 3·고용 2·외 M건"으로 (건수 desc, 상위 3 + 나머지 합산). */
function formatBreakdown(breakdown) {
  const entries = Object.entries(breakdown).sort((a, b) => b[1] - a[1]);
  const top = entries.slice(0, 3);
  const restCount = entries.slice(3).reduce((sum, [, n]) => sum + n, 0);
  let str = top.map(([cat, n]) => `${cat} ${n}`).join("·");
  if (restCount > 0) str += `·외 ${restCount}건`;
  return str;
}

/** 묶음 푸시 본문: N<=3은 제목 나열, N>=4는 분야 카운트. */
function formatDigestBody(matched, breakdown) {
  if (matched.length <= 3) {
    return matched.map((p) => truncate(p.title, 24)).join(" · ");
  }
  return formatBreakdown(breakdown);
}

module.exports = { computeUserDigest, formatBreakdown, formatDigestBody };
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd functions && npm test`
Expected: PASS (7 tests)

- [ ] **Step 6: 커밋**

```bash
git add functions/digest.js functions/test/digest.test.js functions/package.json
git commit -m "feat(functions): 다이제스트 순수 헬퍼 + 단위 테스트"
```

---

### Task 2: 파이프라인 배치 트리거 함수 (`pipeline/notifier.py`)

`new_policies` 정책당 write를 `new_policy_batches` 회차당 1건 write로 대체.

**Files:**
- Modify: `pipeline/notifier.py` (`notify_new_policy` → `notify_new_batch`)
- Test: `pipeline/tests/test_notifier.py`

**Interfaces:**
- Produces: `notify_new_batch(items: list[PolicyItem], batch: str, run_id: str, db=None) -> None` — `new_policy_batches/{run_id}`에 `{run_id, batch, created_at, policies:[{id,category,subcategory,title}]}` 1건 write. `items`가 비면 아무것도 쓰지 않음.

- [ ] **Step 1: 실패하는 테스트로 교체** — `pipeline/tests/test_notifier.py`의 import와 `test_notify_new_policy_writes_to_firestore`를 아래로 교체:

```python
# 상단 import 교체
from pipeline.notifier import notify_new_batch, _load_service_account
```

```python
# test_notify_new_policy_writes_to_firestore 함수를 아래로 교체
def test_notify_new_batch_writes_single_document():
    mock_db = MagicMock()
    mock_doc_ref = MagicMock()
    mock_db.collection.return_value.document.return_value = mock_doc_ref

    items = [_make_item()]
    notify_new_batch(items, batch="morning", run_id="20260629T093000Z_morning", db=mock_db)

    mock_db.collection.assert_called_with("new_policy_batches")
    mock_db.collection.return_value.document.assert_called_with("20260629T093000Z_morning")
    mock_doc_ref.set.assert_called_once()
    data = mock_doc_ref.set.call_args[0][0]
    assert data["run_id"] == "20260629T093000Z_morning"
    assert data["batch"] == "morning"
    assert len(data["policies"]) == 1
    p = data["policies"][0]
    # policy_id는 본문 id에서 와야 한다(gen2 event.params 모지바케 회피, #1459).
    assert p["id"] == "molit-2026-05-29-001"
    assert p["category"] == "부동산"
    assert p["subcategory"] == "청약"
    assert p["title"] == "청약 제도 개편"


def test_notify_new_batch_empty_items_writes_nothing():
    mock_db = MagicMock()
    notify_new_batch([], batch="morning", run_id="x", db=mock_db)
    mock_db.collection.assert_not_called()
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `python -m pytest pipeline/tests/test_notifier.py -v`
Expected: FAIL — `ImportError: cannot import name 'notify_new_batch'`

- [ ] **Step 3: `pipeline/notifier.py` 구현** — `notify_new_policy` 함수를 아래로 교체:

```python
def notify_new_batch(items, batch: str, run_id: str, db=None) -> None:
    """확인된 새 정책 전체를 new_policy_batches/{run_id} 문서 1건으로 써서 Cloud Function을
    회차당 한 번만 트리거한다(푸시 코얼레싱). items가 비면 아무것도 쓰지 않는다.

    문서 본문에 정책별 id를 저장한다. Cloud Function v2(gen2)의 event.params 는 한글 등
    비ASCII 문서 ID를 모지바케로 깨뜨리므로(firebase-functions#1459), 함수는 이 본문 id를
    써야 알림 policy_id 가 CDN 파일명과 일치한다.
    """
    if not items:
        return
    client = _get_db(db)
    client.collection("new_policy_batches").document(run_id).set({
        "run_id": run_id,
        "batch": batch,  # "morning" | "evening" — 분류 라벨
        "created_at": firestore.SERVER_TIMESTAMP,
        "policies": [
            {
                "id": item.id,
                "category": item.category,
                "subcategory": item.subcategory,
                "title": item.title,
            }
            for item in items
        ],
    })
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `python -m pytest pipeline/tests/test_notifier.py -v`
Expected: PASS (4 tests)

- [ ] **Step 5: 커밋**

```bash
git add pipeline/notifier.py pipeline/tests/test_notifier.py
git commit -m "feat(pipeline): notify_new_batch — 회차당 배치 문서 1건 트리거"
```

---

### Task 3: 파이프라인 notify_pending이 배치로 발송 (`pipeline/main.py`)

`notify_pending()`이 정책별 CDN 확인은 유지하되, 확인된 정책을 모아 `notify_new_batch`를 회차당 한 번 호출하도록 변경.

**Files:**
- Modify: `pipeline/main.py:15`(import), `pipeline/main.py:143-174`(`notify_pending`)
- Test: `pipeline/tests/test_main_notify.py` (신규)

**Interfaces:**
- Consumes: `notify_new_batch(items, batch, run_id, db=None)` (Task 2)

- [ ] **Step 1: 실패하는 테스트 작성** — `pipeline/tests/test_main_notify.py` (신규):

```python
import json
from unittest.mock import patch
import pipeline.main as main


def _write_pending(tmp_path, entries):
    f = tmp_path / "pending_notify.json"
    f.write_text(json.dumps(entries, ensure_ascii=False), encoding="utf-8")
    return f


def test_notify_pending_sends_one_batch_for_confirmed(tmp_path, monkeypatch):
    pending = [
        {"id": "a", "category": "부동산", "subcategory": "청약", "title": "t1", "batch": "morning"},
        {"id": "b", "category": "고용", "subcategory": "고용", "title": "t2", "batch": "morning"},
    ]
    f = _write_pending(tmp_path, pending)
    monkeypatch.setattr(main, "PENDING_FILE", f)
    monkeypatch.setenv("BATCH", "morning")

    with patch.object(main, "_wait_for_cdn", return_value=True), \
         patch.object(main, "notify_new_batch") as mock_batch:
        main.notify_pending()

    # 배치 트리거는 정확히 한 번, 확인된 2건 전부 포함.
    mock_batch.assert_called_once()
    items = mock_batch.call_args[0][0]
    assert [i.id for i in items] == ["a", "b"]
    # 전부 성공 시 pending 파일 삭제.
    assert not f.exists()


def test_notify_pending_requeues_cdn_failures(tmp_path, monkeypatch):
    pending = [
        {"id": "a", "category": "부동산", "subcategory": "청약", "title": "t1", "batch": "morning"},
        {"id": "b", "category": "고용", "subcategory": "고용", "title": "t2", "batch": "morning"},
    ]
    f = _write_pending(tmp_path, pending)
    monkeypatch.setattr(main, "PENDING_FILE", f)
    monkeypatch.setenv("BATCH", "morning")

    # a만 CDN 반영, b는 타임아웃.
    def fake_cdn(pid, **kw):
        return pid == "a"

    with patch.object(main, "_wait_for_cdn", side_effect=fake_cdn), \
         patch.object(main, "notify_new_batch") as mock_batch:
        try:
            main.notify_pending()
        except SystemExit:
            pass  # 실패분이 있으면 SystemExit (CI 실패 표시)

    items = mock_batch.call_args[0][0]
    assert [i.id for i in items] == ["a"]  # 확인된 a만 발송
    remaining = json.loads(f.read_text(encoding="utf-8"))
    assert [p["id"] for p in remaining] == ["b"]  # b는 재시도용으로 남음
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `python -m pytest pipeline/tests/test_main_notify.py -v`
Expected: FAIL — `notify_pending`이 아직 `notify_new_batch`를 호출하지 않음 (`mock_batch.assert_called_once` 실패)

- [ ] **Step 3: import 교체** — `pipeline/main.py:15`:

```python
from pipeline.notifier import notify_new_batch
```

- [ ] **Step 4: `notify_pending` 교체** — `pipeline/main.py`의 `notify_pending` 함수 전체를 아래로 교체:

```python
def notify_pending() -> None:
    """docs/ push 이후 호출. 정책별 CDN 반영을 확인한 뒤, 확인된 정책 전체를 배치 1건으로
    트리거한다(회차당 1푸시 코얼레싱)."""
    if not PENDING_FILE.exists():
        print("대기 중인 알림 없음")
        return
    pending = json.loads(PENDING_FILE.read_text(encoding="utf-8"))
    failures = []
    confirmed_raw = []
    for p in pending:
        # CDN 반영 확인 실패 시 알림을 보내지 않는다. 타임아웃이면 실패 목록에 넣어
        # 다음 자동 실행에서 재시도한다(탭 시 404 방지).
        if not _wait_for_cdn(p["id"]):
            print(f"  CDN 반영 확인 실패(타임아웃), 알림 미발송: {p['title']}", file=sys.stderr)
            failures.append(p)
            continue
        confirmed_raw.append(p)

    if confirmed_raw:
        batch_label = os.environ.get("BATCH", "morning")
        run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + f"_{batch_label}"
        items = [
            PolicyItem(
                id=p["id"], category=p["category"], subcategory=p["subcategory"],
                title=p["title"], source="", source_url="",
                file_url=None, file_type=None, published_at="",
            )
            for p in confirmed_raw
        ]
        try:
            notify_new_batch(items, batch=batch_label, run_id=run_id)
            print(f"  FCM 배치 트리거 완료: {len(items)}건 (run_id={run_id})")
        except Exception as e:
            print(f"  FCM 배치 트리거 실패: {e}", file=sys.stderr)
            failures.extend(confirmed_raw)  # 확인분도 다음 회차 재시도

    if failures:
        # 실패분(CDN 타임아웃 또는 배치 트리거 오류)을 pending에 다시 써 둔다.
        # pipeline/pending_notify.json은 git에 커밋되므로 다음 자동 실행에서 재시도된다.
        PENDING_FILE.write_text(json.dumps(failures, ensure_ascii=False), encoding="utf-8")
        raise SystemExit(f"알림 발송 미완료 {len(failures)}건: {[p['id'] for p in failures]}")
    PENDING_FILE.unlink(missing_ok=True)
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `python -m pytest pipeline/tests/test_main_notify.py pipeline/tests/test_notifier.py -v`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add pipeline/main.py pipeline/tests/test_main_notify.py
git commit -m "feat(pipeline): notify_pending이 확인된 정책을 배치 1건으로 발송"
```

---

### Task 4: Cloud Function 배치 처리 + morningDigest 포맷 통일 (`functions/index.js`)

`onNewPolicy`를 `onNewPolicyBatch`로 대체하고, `morningDigest`가 분야 카운트를 쓰도록 갱신. firestore.rules에 새 컬렉션 규칙 추가.

**Files:**
- Modify: `functions/index.js`
- Modify: `firestore.rules:26-30`

**Interfaces:**
- Consumes: `computeUserDigest`, `formatBreakdown`, `formatDigestBody` (Task 1)

> 통합 테스트(Firestore/FCM)는 자동화하지 않는다 — 순수 로직은 Task 1에서 검증됨. 본 Task는 배포 후 수동 확인(아래 Step 6).

- [ ] **Step 1: 헬퍼 import 추가** — `functions/index.js:5` 다음 줄에 추가:

```js
const { computeUserDigest, formatBreakdown, formatDigestBody } = require("./digest");
```

- [ ] **Step 2: `onNewPolicy` 전체를 `onNewPolicyBatch`로 교체** — `functions/index.js`의 `exports.onNewPolicy = onDocumentCreated("new_policies/{policyId}", ...)` 블록 전체(현 40~198행)를 아래로 교체:

```js
exports.onNewPolicyBatch = onDocumentCreated(
  "new_policy_batches/{runId}",
  async (event) => {
    const data = event.data.data();
    const policies = Array.isArray(data.policies) ? data.policies : [];
    const db = getFirestore();

    console.log(`[onNewPolicyBatch] START runId=${event.params.runId} policies=${policies.length}`);

    if (policies.length === 0) {
      await event.data.ref.delete();
      return;
    }

    // 배치 정책들의 category·subcategory 합집합으로 구독자 조회.
    // array-contains-any 는 값 30개 제한 → 30개씩 청크로 나눠 조회 후 doc.id로 머지(중복 제거).
    const matchValues = [
      ...new Set(policies.flatMap((p) => [p.subcategory, p.category]).filter(Boolean)),
    ];
    const usersById = new Map();
    for (let i = 0; i < matchValues.length; i += 30) {
      const chunk = matchValues.slice(i, i + 30);
      const snap = await db
        .collection("users")
        .where("subscribed_categories", "array-contains-any", chunk)
        .get();
      snap.forEach((doc) => usersById.set(doc.id, doc));
    }
    const userDocs = [...usersById.values()];
    console.log(`[onNewPolicyBatch] matched ${userDocs.length} subscribers`);

    // 사용자별 매칭 다이제스트 산출(구독 분야에 매칭되는 정책만).
    const digests = userDocs
      .map((doc) => ({
        doc,
        user: doc.data(),
        ...computeUserDigest(doc.data().subscribed_categories, policies),
      }))
      .filter((d) => d.count > 0);

    // 알림함 기록: (사용자 × 매칭 정책)마다 notifications/{policyId}. 배치 500개 제한 청크.
    const notifWrites = [];
    digests.forEach((d) => {
      d.matched.forEach((p) => {
        notifWrites.push({ userRef: d.doc.ref, policy: p });
      });
    });
    for (let i = 0; i < notifWrites.length; i += 500) {
      const batch = db.batch();
      notifWrites.slice(i, i + 500).forEach(({ userRef, policy }) => {
        batch.set(userRef.collection("notifications").doc(policy.id), {
          title: policy.title,
          category: policy.category,
          subcategory: policy.subcategory,
          received_at: FieldValue.serverTimestamp(),
          read: false,
        });
      });
      await batch.commit();
    }
    console.log(`[onNewPolicyBatch] wrote ${notifWrites.length} notification records`);

    // 트리거 문서 삭제 (중복 방지). 현 설계와 동일 수준의 at-least-once 수용.
    await event.data.ref.delete();

    // 야간 방해금지(KST 22:00~09:00): 즉시 푸시하지 않고 사용자별 누적만. morningDigest가 발송.
    if (isQuietHoursKst()) {
      for (let i = 0; i < digests.length; i += 500) {
        const batch = db.batch();
        digests.slice(i, i + 500).forEach((d) => {
          const update = { overnight_pending: FieldValue.increment(d.count) };
          for (const [cat, n] of Object.entries(d.breakdown)) {
            update[`overnight_breakdown.${cat}`] = FieldValue.increment(n);
          }
          batch.update(d.doc.ref, update);
        });
        await batch.commit();
      }
      console.log(`[onNewPolicyBatch] Quiet hours — deferred to morning digest (${digests.length} users).`);
      return;
    }

    // 주간: 사용자별 푸시 1건. 내용이 사용자마다 다르므로 sendEach(토큰별 메시지 배열).
    const messages = [];
    const messageTokens = [];
    digests.forEach((d) => {
      if (!d.user.fcm_token) return;
      messageTokens.push(d.user.fcm_token);
      if (d.count === 1) {
        const p = d.matched[0];
        const title = `새 ${p.category} 정책`;
        messages.push({
          token: d.user.fcm_token,
          notification: { title, body: p.title },
          data: {
            policy_id: p.id,
            category: p.category,
            subcategory: p.subcategory,
            title,
            body: p.title,
          },
          android: { priority: "high", notification: { channelId: FCM_CHANNEL_ID } },
        });
      } else {
        messages.push({
          token: d.user.fcm_token,
          notification: {
            title: `새 정책 ${d.count}건`,
            body: formatDigestBody(d.matched, d.breakdown),
          },
          data: { open_tab: "history" },
          android: { priority: "high", notification: { channelId: FCM_CHANNEL_ID } },
        });
      }
    });

    if (messages.length === 0) {
      console.log(`[onNewPolicyBatch] No tokens to send.`);
      return;
    }

    const expiredTokens = [];
    for (let i = 0; i < messages.length; i += 500) {
      const chunk = messages.slice(i, i + 500);
      const tokenChunk = messageTokens.slice(i, i + 500);
      let response;
      try {
        response = await getMessaging().sendEach(chunk);
      } catch (err) {
        console.error(`[onNewPolicyBatch] sendEach THREW for chunk ${i}: ${err.message} — continuing`);
        continue;
      }
      console.log(`[onNewPolicyBatch] Sent ${response.successCount}/${chunk.length}, failures=${response.failureCount}`);
      response.responses.forEach((resp, idx) => {
        if (!resp.success && resp.error?.code === "messaging/registration-token-not-registered") {
          expiredTokens.push(tokenChunk[idx]);
        }
      });
    }

    // 만료 토큰 정리 (Firestore `in` 은 최대 30개).
    for (let j = 0; j < expiredTokens.length; j += 30) {
      const tokenChunk = expiredTokens.slice(j, j + 30);
      const expiredSnap = await db.collection("users").where("fcm_token", "in", tokenChunk).get();
      const cleanup = db.batch();
      expiredSnap.forEach((doc) => cleanup.update(doc.ref, { fcm_token: null }));
      await cleanup.commit();
    }

    console.log(`[onNewPolicyBatch] DONE runId=${event.params.runId}`);
  }
);
```

- [ ] **Step 3: `morningDigest` 본문/리셋을 분야 카운트로 갱신** — `functions/index.js`의 `morningDigest` 내부에서 메시지 본문을 만드는 부분을 아래로 교체.

기존:
```js
      if (user.fcm_token && count > 0) {
        messageTokens.push(user.fcm_token);
        messages.push({
          token: user.fcm_token,
          notification: {
            title: "정책 알리미",
            body: `밤사이 새 정책 ${count}건이 도착했어요`,
          },
          // 묶음 알림은 특정 정책이 아니므로, 탭하면 앱의 알림 탭으로 연다.
          data: { open_tab: "history" },
          android: {
            priority: "high",
            notification: { channelId: FCM_CHANNEL_ID },
          },
        });
      }
```

교체:
```js
      if (user.fcm_token && count > 0) {
        // 야간 누적은 제목을 보관하지 않으므로(밤새 배열 증가 write 회피) 분야 카운트 형식만 쓴다.
        // overnight_breakdown 이 있으면 "(부동산 3·고용 2)"를 덧붙이고, 없으면(구버전) 건수만.
        const breakdown = user.overnight_breakdown || {};
        const detail = Object.keys(breakdown).length > 0 ? ` (${formatBreakdown(breakdown)})` : "";
        messageTokens.push(user.fcm_token);
        messages.push({
          token: user.fcm_token,
          notification: {
            title: "정책 알리미",
            body: `밤사이 새 정책 ${count}건${detail}`,
          },
          // 묶음 알림은 특정 정책이 아니므로, 탭하면 앱의 알림 탭으로 연다.
          data: { open_tab: "history" },
          android: {
            priority: "high",
            notification: { channelId: FCM_CHANNEL_ID },
          },
        });
      }
```

- [ ] **Step 4: `morningDigest` 리셋에 `overnight_breakdown` 추가** — `functions/index.js`의 morningDigest 리셋 루프에서 `overnight_pending` 삭제 줄을 아래로 교체.

기존:
```js
        batch.update(ref, { overnight_pending: FieldValue.delete() });
```

교체:
```js
        batch.update(ref, {
          overnight_pending: FieldValue.delete(),
          overnight_breakdown: FieldValue.delete(),
        });
```

- [ ] **Step 5: firestore.rules 갱신** — `firestore.rules:26-30`을 아래로 교체:

```
    // new_policy_batches — 파이프라인 → Cloud Function 내부 트리거용(회차당 배치 1건)
    // Admin SDK가 쓰고 삭제하므로 클라이언트 접근 전면 차단
    match /new_policy_batches/{runId} {
      allow read, write: if false;
    }
```

- [ ] **Step 6: 구문 검사 + 배포 + 수동 확인**

```bash
node --check functions/index.js
cd functions && npm test
```
Expected: `node --check` 무출력(통과), 테스트 PASS.

배포(사용자 권한 필요):
```bash
npx firebase-tools deploy --only functions:onNewPolicyBatch,functions:morningDigest,firestore:rules
```

수동 확인(배포 후 다음 파이프라인 회차 또는 수동 트리거):
- Firebase Functions 로그에서 `[onNewPolicyBatch] START ... policies=N` → `Sent X/Y` 확인.
- 구독자 단말에서: 회차에 1건이면 상세 푸시, 2건+이면 `새 정책 N건` 묶음 1건 수신 확인.
- 앱 알림 탭에 정책별 항목이 각각 쌓였는지 확인.
- 구 `onNewPolicy`는 더 이상 트리거되지 않음(컬렉션 미사용) — 콘솔에서 함수 삭제 여부 확인.

- [ ] **Step 7: 커밋**

```bash
git add functions/index.js firestore.rules
git commit -m "feat(functions): onNewPolicyBatch 회차 단위 묶음 푸시 + morningDigest 분야 카운트"
```

---

## Self-Review

**Spec coverage:**
- 배치 문서 스키마 → Task 2. ✅
- 파이프라인 회차당 1건 트리거 + CDN 확인 유지 + 재시도 → Task 3. ✅
- onNewPolicyBatch: 합집합 조회(30 청크)/사용자별 매칭/알림함 기록/야간 누적/주간 발송 → Task 4. ✅
- 푸시 형태 1건/2~3건/4건+ → `formatDigestBody` (Task 1) + Task 4 분기. ✅
- 건수=구독 분야만 → `computeUserDigest` (Task 1). ✅
- morningDigest 포맷 통일 + overnight_breakdown 누적/리셋 → Task 4 Step 3·4 + onNewPolicyBatch 야간. ✅
- 테스트(pipeline notifier, functions 헬퍼) → Task 1·2·3. ✅
- firestore.rules → Task 4 Step 5. ✅

**Placeholder scan:** 모든 코드 스텝에 실제 코드 포함, "TODO/적절히 처리" 없음. ✅

**Type consistency:** `notify_new_batch(items, batch, run_id, db)` — Task 2 정의 = Task 3 호출 일치. `computeUserDigest`/`formatBreakdown`/`formatDigestBody` — Task 1 정의 = Task 4 사용 시그니처 일치. `overnight_breakdown` 맵 — onNewPolicyBatch 야간 누적(dotted-path increment) = morningDigest 읽기/리셋 일치. ✅
