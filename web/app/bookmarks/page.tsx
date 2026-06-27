"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useAuth } from "@/components/AuthProvider";
import { LoginGate } from "@/components/LoginGate";
import { resolveBookmarks } from "@/lib/bookmarkResolver";
import { catEmoji } from "@/lib/categoryMeta";
import { formatDate } from "@/lib/format";
import type { PolicyDetail } from "@/lib/types";

export default function BookmarksPage() {
  return (
    <>
      <header className="mb-6 border-b border-rule pb-4">
        <p className="doc-eyebrow mb-2">내 보관함</p>
        <h1 className="font-serif text-3xl font-bold leading-tight text-ink">
          담아둔 정책
        </h1>
      </header>
      <LoginGate>
        <BookmarkList />
      </LoginGate>
    </>
  );
}

function BookmarkList() {
  const { user } = useAuth();
  const [items, setItems] = useState<PolicyDetail[] | null>(null);

  useEffect(() => {
    if (!user) return;
    resolveBookmarks(user.uid).then(setItems).catch(() => setItems([]));
  }, [user]);

  if (items === null) {
    return <p className="py-12 text-center font-mono text-xs text-faint">불러오는 중…</p>;
  }
  if (items.length === 0) {
    return (
      <p className="py-16 text-center text-sm text-muted">
        관심 있는 정책을 보관함에 담으면 여기 모입니다.
      </p>
    );
  }

  return (
    <div>
      {items.map((p) => (
        <Link
          key={p.id}
          href={`/policy/${p.id}`}
          className="notice block border-b border-rule px-1 py-4"
        >
          <div className="mb-1.5 flex items-center gap-3 font-mono text-[0.7rem] text-muted">
            <time>{formatDate(p.published_at)}</time>
            <span className="text-rule-strong">|</span>
            <span className="text-ink-soft">
              {catEmoji(p.category)} {p.category}
            </span>
          </div>
          <h3 className="line-clamp-2 font-semibold leading-snug text-ink">
            {p.title}
          </h3>
        </Link>
      ))}
    </div>
  );
}
