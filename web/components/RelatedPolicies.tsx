// 관련 정책 목록 — 상세 페이지 하단의 내부 링크 섹션.
import Link from "next/link";
import type { PolicyItem } from "@/lib/types";
import { formatDate } from "@/lib/format";
import { CategoryIcon } from "./icons";

export function RelatedPolicies({ items }: { items: PolicyItem[] }) {
  if (items.length === 0) return null;
  return (
    <section className="mt-10">
      <p className="mb-3 text-[1.02rem] font-bold text-ink">함께 보면 좋은 정책</p>
      <ul className="flex flex-col gap-2.5">
        {items.map((p) => (
          <li key={p.id}>
            <Link
              href={`/policy/${p.id}`}
              className="card card-hover group block break-keep p-4"
            >
              <div className="mb-1.5 flex items-center gap-2 text-xs">
                <span className="inline-flex items-center gap-1 font-bold text-seal">
                  <CategoryIcon cat={p.category} className="h-3.5 w-3.5" />
                  {p.category}
                </span>
                <span className="font-medium text-faint">
                  {formatDate(p.published_at)}
                </span>
              </div>
              <h3 className="line-clamp-2 text-[0.95rem] font-semibold leading-snug text-ink transition group-hover:text-seal">
                {p.title}
              </h3>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}
