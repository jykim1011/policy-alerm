// 정책 목록의 공고 행 — mono 날짜·분류 + 제목 + 요약 + 부처 직인.
import Link from "next/link";
import type { PolicyItem } from "@/lib/types";
import { catEmoji } from "@/lib/categoryMeta";
import { formatDate } from "@/lib/format";
import { Seal } from "./Seal";

export function PolicyCard({ p }: { p: PolicyItem }) {
  return (
    <Link
      href={`/policy/${p.id}`}
      className="notice group block rounded-lg border-b border-rule px-2 py-5"
    >
      <div className="mb-1.5 flex items-center gap-2.5 text-xs text-faint">
        <time>{formatDate(p.published_at)}</time>
        <span className="text-rule-strong">·</span>
        <span className="font-medium text-seal-ink">
          {catEmoji(p.category)} {p.category}
        </span>
      </div>
      <h3 className="mb-2 line-clamp-2 break-keep text-[1.13rem] font-semibold leading-[1.45] tracking-[-0.01em] text-ink group-hover:text-seal">
        {p.title}
      </h3>
      <p className="mb-3 line-clamp-2 break-keep text-[0.95rem] leading-[1.7] text-ink-soft">
        {p.summary_preview}
      </p>
      <Seal source={p.source} />
    </Link>
  );
}
