"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useAuth } from "@/components/AuthProvider";
import { LoginGate } from "@/components/LoginGate";
import { resolveBookmarks } from "@/lib/bookmarkResolver";
import { CategoryIcon } from "@/components/icons";
import { formatDate } from "@/lib/format";
import type { PolicyDetail } from "@/lib/types";

export default function BookmarksPage() {
  return (
    <div className="mx-auto max-w-2xl">
      <header className="mb-5">
        <p className="doc-eyebrow mb-1.5">내 보관함</p>
        <h1 className="text-[1.6rem] font-extrabold leading-tight tracking-tight text-ink md:text-3xl">
          담아둔 정책
        </h1>
      </header>
      <LoginGate>
        <BookmarkList />
      </LoginGate>
    </div>
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
    return <p className="py-12 text-center text-xs font-medium text-faint">불러오는 중…</p>;
  }
  if (items.length === 0) {
    return (
      <p className="card px-6 py-16 text-center text-sm text-muted">
        관심 있는 정책을 보관함에 담으면 여기 모입니다.
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-2.5">
      {items.map((p) => (
        <Link
          key={p.id}
          href={`/policy/${p.id}`}
          className="card card-hover group block p-4"
        >
          <div className="mb-1.5 flex items-center gap-2 text-xs">
            <span className="inline-flex items-center gap-1 font-bold text-seal">
              <CategoryIcon cat={p.category} className="h-3.5 w-3.5" />
              {p.category}
            </span>
            <time className="font-medium text-faint">
              {formatDate(p.published_at)}
            </time>
          </div>
          <h3 className="line-clamp-2 break-keep font-semibold leading-snug text-ink transition group-hover:text-seal">
            {p.title}
          </h3>
        </Link>
      ))}
    </div>
  );
}
