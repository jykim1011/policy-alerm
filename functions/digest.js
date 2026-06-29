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
