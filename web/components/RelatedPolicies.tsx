// 관련 정책 목록 — 상세 페이지 하단의 내부 링크 섹션.
import Link from "next/link";
import type { PolicyItem } from "@/lib/types";
import { catEmoji } from "@/lib/categoryMeta";
import { formatDate } from "@/lib/format";

export function RelatedPolicies({ items }: { items: PolicyItem[] }) {
  if (items.length === 0) return null;
  return (
    <section className="mt-10 border-t border-rule pt-6">
      <p className="doc-eyebrow mb-3">함께 보면 좋은 정책</p>
      <ul className="flex flex-col">
        {items.map((p) => (
          <li key={p.id}>
            <Link
              href={`/policy/${p.id}`}
              className="notice block break-keep rounded-md border-b border-rule px-2 py-3"
            >
              <div className="mb-1 flex items-center gap-2 text-xs text-faint">
                <span className="font-medium text-seal-ink">
                  {catEmoji(p.category)} {p.category}
                </span>
                <span>·</span>
                <span>{formatDate(p.published_at)}</span>
              </div>
              <h3 className="line-clamp-2 text-[0.95rem] font-medium leading-snug text-ink">
                {p.title}
              </h3>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}
