// 홈 목록 — 분류 색인 + 검색. SSG 초기 목록을 렌더하고 마운트 후 CDN 으로 최신 갱신.
"use client";

import { useEffect, useMemo, useState } from "react";
import type { PolicyItem } from "@/lib/types";
import { CATEGORY_LIST } from "@/lib/categoryMeta";
import { CDN_BASE } from "@/lib/site";
import { PolicyCard } from "./PolicyCard";
import { BuildingIcon, CategoryIcon, ChevronDownIcon, SearchIcon } from "./icons";

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
      {/* 검색 — 흰색 라운드 바 + 돋보기 아이콘 */}
      <div className="relative mb-4">
        <SearchIcon className="pointer-events-none absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-faint" />
        <input
          type="search"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="제목·내용으로 정책 찾기"
          className="card w-full py-3.5 pl-11 pr-4 text-[0.95rem] text-ink outline-none ring-seal/60 transition placeholder:text-faint focus:ring-2"
        />
      </div>

      {/* 분류 칩 — 모바일 가로 스크롤, 데스크톱 줄바꿈 */}
      <div className="no-scrollbar -mx-4 mb-3 flex gap-2 overflow-x-auto px-4 sm:mx-0 sm:flex-wrap sm:overflow-visible sm:px-0">
        {CATEGORY_LIST.map((c) => {
          const active = cat === c.key;
          return (
            <button
              key={c.key}
              onClick={() => setCat(c.key)}
              aria-pressed={active}
              className={`inline-flex shrink-0 items-center gap-1.5 rounded-full px-3.5 py-2 text-[0.85rem] font-semibold transition ${
                active
                  ? "bg-ink text-white"
                  : "bg-surface text-muted shadow-card hover:text-ink"
              }`}
            >
              <CategoryIcon cat={c.key} className="h-4 w-4" />
              {c.key}
            </button>
          );
        })}
      </div>

      <div className="mb-5 flex items-center gap-2">
        <div
          className={`relative ${
            src !== "전체" ? "text-seal-ink" : "text-muted"
          }`}
        >
          <BuildingIcon className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2" />
          <select
            value={src}
            onChange={(e) => setSrc(e.target.value)}
            aria-label="주관부처 필터"
            className={`appearance-none rounded-full py-2 pl-9 pr-9 text-[0.85rem] font-semibold outline-none transition ${
              src !== "전체"
                ? "bg-seal-soft"
                : "bg-surface shadow-card hover:text-ink"
            }`}
          >
            <option value="전체">주관부처 전체</option>
            {sources.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
          <ChevronDownIcon className="pointer-events-none absolute right-3.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2" />
        </div>
        {src !== "전체" && (
          <button
            onClick={() => setSrc("전체")}
            className="text-xs font-semibold text-faint hover:text-seal"
          >
            초기화
          </button>
        )}
        <p className="ml-auto text-xs font-medium text-faint">
          수록 {filtered.length}건
        </p>
      </div>

      <div className="grid grid-cols-1 gap-3 md:grid-cols-2 md:gap-4">
        {filtered.map((p) => (
          <PolicyCard key={p.id} p={p} />
        ))}
      </div>
      {filtered.length === 0 && (
        <p className="py-16 text-center text-sm text-muted">
          조건에 맞는 정책이 없습니다.
        </p>
      )}
    </div>
  );
}
