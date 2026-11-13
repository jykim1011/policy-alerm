// 빌드 타임(SSG) 시장 부가 데이터 — docs/market/*.json (pipeline/market_stats.py 가 매일 갱신).
// 파일이 없거나 깨져도 상세 페이지는 그대로 나와야 하므로 항상 빈 값으로 폴백한다.

import fs from "node:fs";
import path from "node:path";
import type { Disclosure, PolicyReaction, SectorStats } from "./types";

const MARKET_DIR = path.join(process.cwd(), "..", "docs", "market");

const cache = new Map<string, unknown>();

function read<T>(name: string, fallback: T): T {
  if (cache.has(name)) return cache.get(name) as T;
  let v = fallback;
  try {
    const file = path.join(MARKET_DIR, name);
    if (fs.existsSync(file)) v = JSON.parse(fs.readFileSync(file, "utf-8")) as T;
  } catch {
    v = fallback;
  }
  cache.set(name, v);
  return v;
}

export function getSectorStats(): Record<string, SectorStats> {
  return read<{ sectors?: Record<string, SectorStats> }>("sector_stats.json", {}).sectors ?? {};
}

export function getMinSamples(): number {
  return read<{ min_samples?: number }>("sector_stats.json", {}).min_samples ?? 3;
}

/** 이 정책의 업종별 발표 후 반응. key = 업종. */
export function getPolicyReactions(policyId: string): Record<string, PolicyReaction> {
  const items =
    read<{ items?: Record<string, PolicyReaction & { policy_id: string }> }>("reactions.json", {})
      .items ?? {};
  const out: Record<string, PolicyReaction> = {};
  for (const r of Object.values(items)) if (r.policy_id === policyId) out[r.sector] = r;
  return out;
}

/** 종목코드 → 정책 발표일 이후 공시(최신순). */
export function getDisclosuresSince(code: string, sinceIso: string): Disclosure[] {
  const stocks =
    read<{ stocks?: Record<string, Disclosure[]> }>("disclosures.json", {}).stocks ?? {};
  const since = sinceIso.slice(0, 10);
  return (stocks[code] ?? []).filter((d) => d.date >= since);
}
