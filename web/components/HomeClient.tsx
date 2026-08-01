// 홈 목록 — 분류 색인 + 검색 + 페이징.
// SSG 초기 목록(최신 50)을 렌더하고 마운트 후 CDN 으로 최신 갱신.
// "더 보기"를 누르거나 검색어를 입력하면 CDN 아카이브(전체 연도)를 1회
// 로드해 이어 붙인다 — 서버 없이 과거 데이터 열람·전체 검색을 지원.
"use client";

import { Fragment, useCallback, useEffect, useMemo, useState } from "react";
import type { PolicyItem } from "@/lib/types";
import { CATEGORY_LIST, matchesCategory } from "@/lib/categoryMeta";
import { CDN_BASE } from "@/lib/site";
import { PolicyCard } from "./PolicyCard";
import { BuildingIcon, CategoryIcon, ChevronDownIcon, SearchIcon } from "./icons";

const PAGE_SIZE = 50;

// 피드 날짜 그룹 라벨. relative=true 면 오늘/어제를 우선 사용한다.
// SSG HTML 과 첫 클라이언트 렌더의 불일치(hydration mismatch)를 피하려고
// 마운트 전에는 발행일만으로 결정되는 절대 라벨을 쓴다.
function dateLabel(iso: string, relative: boolean): string {
  const d = new Date(iso);
  if (isNaN(d.getTime())) return iso;
  if (relative) {
    const startOfDay = (x: Date) =>
      new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime();
    const diff = Math.round((startOfDay(new Date()) - startOfDay(d)) / 86400000);
    if (diff <= 0) return "오늘";
    if (diff === 1) return "어제";
  }
  const year =
    d.getFullYear() !== new Date().getFullYear() ? `${d.getFullYear()}년 ` : "";
  return `${year}${d.getMonth() + 1}월 ${d.getDate()}일 (${"일월화수목금토"[d.getDay()]})`;
}

export function HomeClient({ initial }: { initial: PolicyItem[] }) {
  const [policies, setPolicies] = useState<PolicyItem[]>(initial);
  const [archive, setArchive] = useState<PolicyItem[] | null>(null);
  const [archiveLoading, setArchiveLoading] = useState(false);
  const [visible, setVisible] = useState(PAGE_SIZE);
  const [mounted, setMounted] = useState(false);
  const [cat, setCat] = useState("전체");
  const [src, setSrc] = useState("전체");
  const [q, setQ] = useState("");

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setMounted(true);
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

  // CDN 아카이브(연도별 JSON) 1회 로드 — 더 보기·검색 시 호출.
  const loadArchive = useCallback(async () => {
    if (archive || archiveLoading) return;
    setArchiveLoading(true);
    try {
      const idx = await fetch(`${CDN_BASE}/archive/index.json`).then((r) =>
        r.ok ? r.json() : null,
      );
      const years: number[] = idx?.years ?? [];
      const byId = new Map<string, PolicyItem>();
      for (const y of years) {
        try {
          const d = await fetch(`${CDN_BASE}/archive/${y}.json`).then((r) =>
            r.ok ? r.json() : null,
          );
          for (const it of d?.items ?? []) byId.set(it.id, it as PolicyItem);
        } catch {
          /* 연도 하나 실패해도 나머지는 표시 */
        }
      }
      if (byId.size > 0) setArchive([...byId.values()]);
    } catch {
      /* 아카이브 로드 실패 — 최신 목록만 유지 */
    } finally {
      setArchiveLoading(false);
    }
  }, [archive, archiveLoading]);

  // URL 의 ?q= 검색어로 초기화 (구글 사이트링크 검색창·공유 가능한 검색 URL 지원).
  // 검색 진입이므로 전체 아카이브도 함께 불러 전체 검색이 되게 한다. 1회만 실행.
  useEffect(() => {
    const initial = new URLSearchParams(window.location.search).get("q");
    if (initial) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setQ(initial);
      loadArchive();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 검색어 변경 — 전체 아카이브를 대상으로 찾도록 자동 로드하고 페이지를 되돌린다.
  function changeQuery(v: string) {
    setQ(v);
    setVisible(PAGE_SIZE);
    if (v.trim()) loadArchive();
  }

  // 최신 목록 + 아카이브 병합(id 중복 제거, 발행일 내림차순).
  const combined = useMemo(() => {
    if (!archive) return policies;
    const byId = new Map<string, PolicyItem>();
    for (const p of archive) byId.set(p.id, p);
    for (const p of policies) byId.set(p.id, p);
    return [...byId.values()].sort((a, b) =>
      b.published_at.localeCompare(a.published_at),
    );
  }, [policies, archive]);

  // 현재 목록에 등장하는 주관부처(빈도 내림차순) — 드롭다운 옵션.
  const sources = useMemo(() => {
    const counts = new Map<string, number>();
    for (const p of combined) {
      const s = p.source?.trim();
      if (s) counts.set(s, (counts.get(s) ?? 0) + 1);
    }
    return [...counts.entries()].sort((a, b) => b[1] - a[1]).map(([s]) => s);
  }, [combined]);

  const filtered = useMemo(() => {
    const needle = q.trim().toLowerCase();
    return combined.filter((p) => {
      if (!matchesCategory(p, cat)) return false;
      if (src !== "전체" && p.source !== src) return false;
      if (!needle) return true;
      return (
        p.title.toLowerCase().includes(needle) ||
        p.summary_preview.toLowerCase().includes(needle)
      );
    });
  }, [combined, cat, src, q]);

  const shown = filtered.slice(0, visible);
  // 더 보기: 아직 안 보인 항목이 있거나, 아카이브를 아직 안 불러왔으면 노출.
  const hasMore = filtered.length > visible || (!archive && !archiveLoading);

  // 날짜별 그룹 — 발행일이 같은 카드끼리 섹션 헤더로 묶는다.
  const groups = useMemo(() => {
    const out: { label: string; items: PolicyItem[] }[] = [];
    for (const p of shown) {
      const label = dateLabel(p.published_at, mounted);
      const last = out[out.length - 1];
      if (last && last.label === label) last.items.push(p);
      else out.push({ label, items: [p] });
    }
    return out;
  }, [shown, mounted]);

  async function loadMore() {
    if (!archive) await loadArchive();
    setVisible((v) => v + PAGE_SIZE);
  }

  const needle = q.trim();

  return (
    <div>
      {/* 검색 — 흰색 라운드 바 + 돋보기 아이콘 */}
      <div className="relative mb-4">
        <SearchIcon className="pointer-events-none absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-faint" />
        <input
          type="search"
          value={q}
          onChange={(e) => changeQuery(e.target.value)}
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
              onClick={() => {
                setCat(c.key);
                setVisible(PAGE_SIZE);
              }}
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
            onChange={(e) => {
              setSrc(e.target.value);
              setVisible(PAGE_SIZE);
            }}
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
            onClick={() => {
              setSrc("전체");
              setVisible(PAGE_SIZE);
            }}
            className="text-xs font-semibold text-faint hover:text-seal"
          >
            초기화
          </button>
        )}
        <p className="ml-auto text-xs font-medium text-faint">
          {archive ? `전체 ${filtered.length}건` : `최신 ${filtered.length}건`}
        </p>
      </div>

      <div className="grid grid-cols-1 gap-3 md:grid-cols-2 md:gap-4">
        {groups.map((g) => (
          <Fragment key={g.label}>
            <p className="mt-3 text-sm font-bold text-muted first:mt-0 md:col-span-2">
              {g.label}
            </p>
            {g.items.map((p) => (
              <PolicyCard key={p.id} p={p} highlight={needle} />
            ))}
          </Fragment>
        ))}
      </div>

      {filtered.length === 0 && (
        <p className="py-16 text-center text-sm text-muted">
          {archiveLoading
            ? "전체 정책에서 찾는 중…"
            : "조건에 맞는 정책이 없습니다."}
        </p>
      )}

      {filtered.length > 0 && hasMore && (
        <button
          onClick={loadMore}
          disabled={archiveLoading}
          className="card card-hover mx-auto mt-6 block rounded-full px-8 py-3 text-sm font-bold text-ink-soft transition hover:text-seal disabled:opacity-60"
        >
          {archiveLoading ? "불러오는 중…" : "지난 정책 더 보기"}
        </button>
      )}
    </div>
  );
}
