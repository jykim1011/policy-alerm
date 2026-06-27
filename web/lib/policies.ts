// 빌드 타임(SSG) 데이터 레이어. 같은 레포의 docs/ 정적 JSON 을 직접 읽는다.
// (CDN fetch 대신 로컬 파일을 읽어 항상 최신 커밋을 반영하고 네트워크 지연이 없다.)
// 이 모듈은 서버 컴포넌트/빌드 단계에서만 import 한다.

import fs from "node:fs";
import path from "node:path";
import type { PolicyDetail, PolicyIndex, PolicyItem } from "./types";

const DOCS_DIR = path.join(process.cwd(), "..", "docs");
const POLICIES_DIR = path.join(DOCS_DIR, "policies");
const ARCHIVE_DIR = path.join(DOCS_DIR, "archive");

// 홈 목록: index.json 의 최신 50건 (앱 홈과 동일).
export function getAllPolicies(): PolicyItem[] {
  const raw = fs.readFileSync(path.join(POLICIES_DIR, "index.json"), "utf-8");
  const index = JSON.parse(raw) as PolicyIndex;
  return index.items ?? [];
}

// 전체(아카이브) 정책 — SEO용 정적 페이지/사이트맵 생성에 사용 (연도별 archive/{year}.json 병합).
export function getArchivedPolicies(): PolicyItem[] {
  const indexFile = path.join(ARCHIVE_DIR, "index.json");
  if (!fs.existsSync(indexFile)) return getAllPolicies();
  const { years } = JSON.parse(fs.readFileSync(indexFile, "utf-8")) as {
    years: number[];
  };
  const byId = new Map<string, PolicyItem>();
  for (const year of years) {
    const file = path.join(ARCHIVE_DIR, `${year}.json`);
    if (!fs.existsSync(file)) continue;
    const data = JSON.parse(fs.readFileSync(file, "utf-8")) as PolicyIndex;
    for (const item of data.items ?? []) byId.set(item.id, item);
  }
  // index.json 최신분도 포함(아카이브 반영 지연 대비).
  for (const item of getAllPolicies()) byId.set(item.id, item);
  return [...byId.values()];
}

export function getPolicyIds(): string[] {
  return getArchivedPolicies().map((p) => p.id);
}

export function getPolicy(id: string): PolicyDetail | null {
  const file = path.join(POLICIES_DIR, `${id}.json`);
  if (!fs.existsSync(file)) return null;
  return JSON.parse(fs.readFileSync(file, "utf-8")) as PolicyDetail;
}

export function getCategoryPolicies(category: string): PolicyItem[] {
  const all = getArchivedPolicies().sort((a, b) =>
    b.published_at.localeCompare(a.published_at),
  );
  if (category === "전체") return all;
  return all.filter((p) => p.category === category);
}

/**
 * 관련 정책 — 같은 분야를 우선하고, 모자라면 같은 부처로 채운다. 자기 자신 제외.
 * 내부 링크를 늘려 thin/고립 페이지를 토픽 클러스터로 묶는다.
 */
export function getRelatedPolicies(
  id: string,
  category: string,
  source: string | undefined,
  limit = 6,
): PolicyItem[] {
  const all = getArchivedPolicies()
    .filter((p) => p.id !== id)
    .sort((a, b) => b.published_at.localeCompare(a.published_at));
  const sameCat = all.filter((p) => p.category === category);
  const seen = new Set(sameCat.map((p) => p.id));
  const sameSource = source
    ? all.filter((p) => p.source === source && !seen.has(p.id))
    : [];
  return [...sameCat, ...sameSource].slice(0, limit);
}

/** 전체 아카이브에 등장하는 주관부처 목록(빈도 내림차순). 부처 허브 생성에 사용. */
export function getAllSources(): { name: string; count: number }[] {
  const counts = new Map<string, number>();
  for (const p of getArchivedPolicies()) {
    const s = p.source?.trim();
    if (s) counts.set(s, (counts.get(s) ?? 0) + 1);
  }
  return [...counts.entries()]
    .map(([name, count]) => ({ name, count }))
    .sort((a, b) => b.count - a.count);
}

export function getSourcePolicies(source: string): PolicyItem[] {
  return getArchivedPolicies()
    .filter((p) => p.source === source)
    .sort((a, b) => b.published_at.localeCompare(a.published_at));
}
