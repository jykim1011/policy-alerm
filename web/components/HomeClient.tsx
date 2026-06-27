// 홈 목록 — 분류 색인 + 검색. SSG 초기 목록을 렌더하고 마운트 후 CDN 으로 최신 갱신.
"use client";

import { useEffect, useMemo, useState } from "react";
import type { PolicyItem } from "@/lib/types";
import { CATEGORY_LIST } from "@/lib/categoryMeta";
import { CDN_BASE } from "@/lib/site";
import { PolicyCard } from "./PolicyCard";

export function HomeClient({ initial }: { initial: PolicyItem[] }) {
  const [policies, setPolicies] = useState<PolicyItem[]>(initial);
  const [cat, setCat] = useState("전체");
  const [src, setSrc] = useState("전체");
  const [q, setQ] = useState("");

  // URL 의 ?q= 검색어로 초기화 (구글 사이트링크 검색창·공유 가능한 검색 URL 지원).
  useEffect(() => {
    const initial = new URLSearchParams(window.location.search).get("q");
    // eslint-disable-next-line react-hooks/set-state-in-effect
    if (initial) setQ(initial);
  }, []);

  useEffect(() => {
    let alive = true;
    fetch(`${CDN_BASE}/policies/index.json`)
      .then((r) => (r.ok ? r.json() : null))
      .then((data) => {
        if (alive && data?.items) setPolicies(data.items as PolicyItem[]);
      })
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, []);

  // 현재 목록에 등장하는 주관부처(빈도 내림차순) — 드롭다운 옵션.
  const sources = useMemo(() => {
    const counts = new Map<string, number>();
    for (const p of policies) {
      const s = p.source?.trim();
      if (s) counts.set(s, (counts.get(s) ?? 0) + 1);
    }
    return [...counts.entries()].sort((a, b) => b[1] - a[1]).map(([s]) => s);
  }, [policies]);

  const filtered = useMemo(() => {
    const needle = q.trim().toLowerCase();
    return policies.filter((p) => {
      if (cat !== "전체" && p.category !== cat) return false;
      if (src !== "전체" && p.source !== src) return false;
      if (!needle) return true;
      return (
        p.title.toLowerCase().includes(needle) ||
        p.summary_preview.toLowerCase().includes(needle)
      );
    });
  }, [policies, cat, src, q]);

  return (
    <div>
      <div className="relative mb-5">
        <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 font-mono text-xs text-faint">
          검색
        </span>
        <input
          type="search"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="제목·내용으로 정책 찾기"
          className="w-full rounded-[4px] border border-rule-strong bg-paper py-2.5 pl-14 pr-3 text-sm text-ink outline-none placeholder:text-faint focus:border-seal"
        />
      </div>

      <div className="mb-1 flex flex-wrap gap-2 border-b border-rule pb-4">
        {CATEGORY_LIST.map((c) => {
          const active = cat === c.key;
          return (
            <button
              key={c.key}
              onClick={() => setCat(c.key)}
              aria-pressed={active}
              className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-[0.82rem] font-medium transition ${
                active
                  ? "border-seal bg-seal text-white shadow-sm"
                  : "border-rule bg-surface text-ink-soft hover:border-seal hover:bg-seal-soft hover:text-seal-ink"
              }`}
            >
              <span aria-hidden="true" className="text-[0.9rem] leading-none">
                {c.emoji}
              </span>
              {c.key}
            </button>
          );
        })}
      </div>

      <div className="mb-1 mt-3 flex items-center gap-2">
        <div className="relative">
          <select
            value={src}
            onChange={(e) => setSrc(e.target.value)}
            aria-label="주관부처 필터"
            className={`appearance-none rounded-full border py-1.5 pl-3.5 pr-8 text-[0.82rem] font-medium outline-none transition ${
              src !== "전체"
                ? "border-seal bg-seal-soft text-seal-ink"
                : "border-rule bg-surface text-ink-soft hover:border-seal"
            }`}
          >
            <option value="전체">🏛 주관부처 전체</option>
            {sources.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
          <span className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[0.7rem] text-faint">
            ▼
          </span>
        </div>
        {src !== "전체" && (
          <button
            onClick={() => setSrc("전체")}
            className="text-xs text-faint hover:text-seal"
          >
            초기화
          </button>
        )}
      </div>

      <p className="mb-1 font-mono text-xs text-faint">
        수록 {filtered.length}건
      </p>

      <div>
        {filtered.map((p) => (
          <PolicyCard key={p.id} p={p} />
        ))}
        {filtered.length === 0 && (
          <p className="py-16 text-center text-sm text-muted">
            조건에 맞는 정책이 없습니다.
          </p>
        )}
      </div>
    </div>
  );
}
