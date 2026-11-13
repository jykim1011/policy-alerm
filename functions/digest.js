/**
 * 순수 다이제스트 헬퍼 — Firestore/FCM 의존 없이 단위 테스트 가능.
 * onNewPolicyBatch(주간)와 morningDigest(야간)가 공유한다.
 */

/** 제목을 max자 초과 시 잘라 …를 붙인다. */
function truncate(s, max) {
  return s.length > max ? s.slice(0, max) + "…" : s;
}

/**
 * 사용자의 구독 분야·관심 업종과 배치 정책을 교차해 매칭 정책/건수/분야별 집계를 낸다.
 * 매칭 규칙: 정책의 subcategory 또는 category가 구독 목록에 있거나,
 * 정책의 관련 업종(sectors) 중 하나가 관심 업종 목록에 있으면 매칭.
 *
 * sectorOnly: 분야 구독은 아니고 관심 업종으로만 걸린 정책 id → 걸린 업종 이름들.
 *   푸시 제목을 "관심 업종 ○○ 관련 정책"으로 바꿀 때 쓴다.
 * marketCount: 매칭 정책 중 관련 업종이 있는(시장 영향) 정책 수.
 */
function computeUserDigest(subscribedCategories, policies, subscribedSectors) {
  const set = new Set(subscribedCategories || []);
  const sectorSet = new Set(subscribedSectors || []);
  const matched = [];
  const sectorOnly = {};
  for (const p of policies) {
    const byCategory = set.has(p.subcategory) || set.has(p.category);
    const hitSectors = (p.sectors || []).filter((s) => sectorSet.has(s));
    if (!byCategory && hitSectors.length === 0) continue;
    matched.push(p);
    if (!byCategory) sectorOnly[p.id] = hitSectors;
  }
  const breakdown = {};
  for (const p of matched) {
    breakdown[p.category] = (breakdown[p.category] || 0) + 1;
  }
  const marketCount = matched.filter((p) => (p.sectors || []).length > 0).length;
  return { matched, count: matched.length, breakdown, sectorOnly, marketCount };
}

/** 단건 푸시 제목. 관심 업종으로만 걸린 정책이면 업종을 앞세운다. */
function singleTitle(policy, sectorOnly) {
  const hit = (sectorOnly || {})[policy.id];
  if (hit && hit.length > 0) return `📈 관심 업종(${hit.slice(0, 2).join("·")}) 관련 정책`;
  return `새 ${policy.category} 정책`;
}

/** 묶음 본문 뒤에 붙이는 시장 관련 건수(" · 📈 시장 관련 2건"). 없으면 빈 문자열. */
function marketSuffix(n) {
  return n > 0 ? ` · 📈 시장 관련 ${n}건` : "";
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

module.exports = { computeUserDigest, formatBreakdown, formatDigestBody, singleTitle, marketSuffix };
