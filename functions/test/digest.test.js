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

const S = (id, category, sectors) => ({ id, category, subcategory: category, title: id, sectors });

test("computeUserDigest: 관심 업종으로도 매칭하고 sectorOnly·marketCount 집계", () => {
  const policies = [
    S("a", "금융", ["증권"]),       // 분야+업종
    S("b", "부동산", ["건설"]),     // 업종만
    S("c", "복지", []),             // 매칭 없음
    S("d", "금융", []),             // 분야만
  ];
  const r = computeUserDigest(["금융"], policies, ["건설", "증권"]);
  assert.deepStrictEqual(r.matched.map((p) => p.id), ["a", "b", "d"]);
  assert.deepStrictEqual(r.sectorOnly, { b: ["건설"] });
  assert.strictEqual(r.marketCount, 2);
});

test("computeUserDigest: sectors 필드가 없는 기존 배치도 안전", () => {
  const r = computeUserDigest(["청약"], [P("a", "부동산", "청약", "x")], ["건설"]);
  assert.strictEqual(r.count, 1);
  assert.strictEqual(r.marketCount, 0);
});

test("singleTitle·marketSuffix", () => {
  const { singleTitle, marketSuffix } = require("../digest");
  assert.strictEqual(singleTitle({ id: "b", category: "부동산" }, { b: ["건설"] }), "📈 관심 업종(건설) 관련 정책");
  assert.strictEqual(singleTitle({ id: "a", category: "금융" }, {}), "새 금융 정책");
  assert.strictEqual(marketSuffix(0), "");
  assert.strictEqual(marketSuffix(2), " · 📈 시장 관련 2건");
});
