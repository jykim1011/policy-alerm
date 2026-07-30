// 정책 카드 — 흰색 라운드 카드. 분류 배지 + 날짜 + 제목 + 요약 + 부처 칩.
import Link from "next/link";
import type { PolicyItem } from "@/lib/types";
import { formatDate } from "@/lib/format";
import { Seal } from "./Seal";
import { CategoryIcon } from "./icons";

export function PolicyCard({ p }: { p: PolicyItem }) {
  return (
    <Link
      href={`/policy/${p.id}`}
      className="card card-hover group flex h-full flex-col p-5"
    >
      <div className="mb-2.5 flex items-center gap-2">
        <span className="inline-flex items-center gap-1 rounded-lg bg-seal-soft px-2 py-1 text-xs font-bold text-seal">
          <CategoryIcon cat={p.category} className="h-3.5 w-3.5" />
          {p.category}
        </span>
        <time className="text-xs font-medium text-faint">
          {formatDate(p.published_at)}
        </time>
      </div>
      <h3 className="mb-1.5 line-clamp-2 break-keep text-[1.05rem] font-bold leading-[1.45] tracking-[-0.01em] text-ink transition group-hover:text-seal">
        {p.title}
      </h3>
      <p className="mb-4 line-clamp-2 break-keep text-sm leading-[1.65] text-muted">
        {p.summary_preview}
      </p>
      <div className="mt-auto">
        <Seal source={p.source} />
      </div>
    </Link>
  );
}
