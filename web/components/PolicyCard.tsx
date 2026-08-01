// 정책 카드 — 흰색 라운드 카드. 분류 배지 + 날짜 + 제목 + 요약 + 부처 칩.
// highlight 에 검색어를 넘기면 제목·요약에서 일치 부분을 표시한다.
import Link from "next/link";
import type { PolicyItem } from "@/lib/types";
import { formatDate } from "@/lib/format";
import { Seal } from "./Seal";
import { CategoryIcon } from "./icons";
import { CommentCount } from "./CommentCount";

function escapeRegExp(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function Highlighted({ text, needle }: { text: string; needle?: string }) {
  if (!needle) return <>{text}</>;
  const parts = text.split(new RegExp(`(${escapeRegExp(needle)})`, "gi"));
  return (
    <>
      {parts.map((s, i) =>
        i % 2 === 1 ? (
          <mark
            key={i}
            className="rounded-[3px] bg-seal-soft px-0.5 text-seal-ink"
          >
            {s}
          </mark>
        ) : (
          s
        ),
      )}
    </>
  );
}

export function PolicyCard({
  p,
  highlight,
}: {
  p: PolicyItem;
  highlight?: string;
}) {
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
        <CommentCount policyId={p.id} className="ml-auto" />
      </div>
      <h3 className="mb-1.5 line-clamp-2 break-keep text-[1.05rem] font-bold leading-[1.45] tracking-[-0.01em] text-ink transition group-hover:text-seal">
        <Highlighted text={p.title} needle={highlight} />
      </h3>
      <p className="mb-4 line-clamp-2 break-keep text-[0.9rem] leading-[1.65] text-muted md:line-clamp-3">
        <Highlighted text={p.summary_preview} needle={highlight} />
      </p>
      <div className="mt-auto">
        <Seal source={p.source} />
      </div>
    </Link>
  );
}
