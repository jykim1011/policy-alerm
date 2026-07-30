// 날짜 포맷 등 표시용 유틸 (서버/클라이언트 공용).

import type { PolicySummary } from "./types";

// 파이프라인(LLM)이 문자열 필드를 객체로 뱉는 사고가 있었다(2026-07-08 when_effective).
// 객체가 React 자식으로 렌더되면 페이지가 죽으므로 읽기 시점에 평탄화한다.
export function flattenStr(v: unknown): string {
  if (v == null) return "";
  if (typeof v === "string") return v;
  if (Array.isArray(v)) return v.map(flattenStr).join(", ");
  if (typeof v === "object") {
    return Object.entries(v)
      .map(([k, x]) => `${k}: ${flattenStr(x)}`)
      .join(", ");
  }
  return String(v);
}

export function sanitizeSummary(s: PolicySummary): PolicySummary {
  s.what_changed = flattenStr(s.what_changed);
  s.who_is_affected = flattenStr(s.who_is_affected);
  if (s.when_effective != null) s.when_effective = flattenStr(s.when_effective);
  if (s.how_to_apply != null) s.how_to_apply = flattenStr(s.how_to_apply);
  if (s.background != null) s.background = flattenStr(s.background);
  if (Array.isArray(s.key_points)) s.key_points = s.key_points.map(flattenStr);
  if (Array.isArray(s.eligibility)) s.eligibility = s.eligibility.map(flattenStr);
  return s;
}

export function formatDate(iso: string): string {
  const d = new Date(iso);
  if (isNaN(d.getTime())) return iso;
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, "0")}.${String(
    d.getDate(),
  ).padStart(2, "0")}`;
}

export function formatRelative(millis: number): string {
  if (!millis) return "";
  const diff = Date.now() - millis;
  const min = Math.floor(diff / 60000);
  if (min < 1) return "방금";
  if (min < 60) return `${min}분 전`;
  const hr = Math.floor(min / 60);
  if (hr < 24) return `${hr}시간 전`;
  const day = Math.floor(hr / 24);
  if (day < 7) return `${day}일 전`;
  const d = new Date(millis);
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, "0")}.${String(
    d.getDate(),
  ).padStart(2, "0")}`;
}
