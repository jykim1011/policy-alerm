import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getPolicy, getPolicyIds } from "@/lib/policies";
import { catEmoji, catMeta, fileMeta } from "@/lib/categoryMeta";
import { formatDate } from "@/lib/format";
import { SITE_NAME, SITE_URL } from "@/lib/site";
import { BookmarkButton } from "@/components/BookmarkButton";
import { ShareButton } from "@/components/ShareButton";
import { CommentSection } from "@/components/CommentSection";
import { Seal } from "@/components/Seal";

export const dynamicParams = false;

export function generateStaticParams() {
  return getPolicyIds().map((id) => ({ id }));
}

type Props = { params: Promise<{ id: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { id } = await params;
  const p = getPolicy(decodeURIComponent(id));
  if (!p) return { title: "정책을 찾을 수 없습니다" };
  const desc = p.summary?.what_changed ?? p.title;
  const url = `${SITE_URL}/policy/${encodeURIComponent(p.id)}/`;
  return {
    title: p.title,
    description: desc.slice(0, 160),
    alternates: { canonical: url },
    openGraph: {
      type: "article",
      title: p.title,
      description: desc.slice(0, 160),
      url,
      siteName: SITE_NAME,
      locale: "ko_KR",
      publishedTime: p.published_at,
    },
  };
}

export default async function PolicyPage({ params }: Props) {
  const { id } = await params;
  const p = getPolicy(decodeURIComponent(id));
  if (!p) notFound();

  const file = fileMeta(p.file_type);
  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "NewsArticle",
    headline: p.title,
    datePublished: p.published_at,
    articleSection: p.category,
    publisher: { "@type": "GovernmentOrganization", name: p.source },
    description: p.summary?.what_changed ?? p.title,
    mainEntityOfPage: `${SITE_URL}/policy/${encodeURIComponent(p.id)}/`,
  };

  return (
    <article>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />

      {/* 공문서 머리글: 분류·문서번호 + 직인 */}
      <div className="mb-4 flex items-start justify-between gap-4 border-b border-rule pb-4">
        <div className="min-w-0">
          <p className="doc-eyebrow mb-2">
            {catEmoji(p.category)} {catMeta(p.category).full}
          </p>
          <div className="flex flex-wrap gap-x-3 gap-y-1 font-mono text-[0.7rem] text-muted">
            <span>발행 {formatDate(p.published_at)}</span>
            <span className="text-rule-strong">·</span>
            <span className="break-all">문서 {p.id}</span>
          </div>
        </div>
        <Seal source={p.source} size="lg" />
      </div>

      <h1 className="mb-5 text-[1.55rem] font-bold leading-snug tracking-tight text-ink sm:text-[1.85rem]">
        {p.title}
      </h1>

      <div className="mb-7 flex flex-wrap gap-2">
        <BookmarkButton policyId={p.id} />
        <ShareButton
          policyId={p.id}
          title={p.title}
          source={p.source}
          publishedAt={p.published_at}
          whatChanged={p.summary?.what_changed}
          whenEffective={p.summary?.when_effective}
        />
        <a
          href={p.source_url}
          target="_blank"
          rel="noopener noreferrer"
          className="rounded-[4px] border border-rule-strong bg-paper px-3 py-1.5 text-sm font-medium text-ink-soft hover:border-seal hover:text-seal"
        >
          원문 보기 ↗
        </a>
        {p.file_url && file && (
          <a
            href={p.file_url}
            target="_blank"
            rel="noopener noreferrer"
            className="rounded-[4px] border border-rule-strong bg-paper px-3 py-1.5 text-sm font-medium hover:border-seal"
            style={{ color: file.color }}
          >
            {file.label} 첨부 ↓
          </a>
        )}
      </div>

      {p.summary && (
        <div className="flex flex-col divide-y divide-rule border-y border-rule">
          <Section title="무엇이 달라지나요" body={p.summary.what_changed} />
          <Section title="누가 영향을 받나요" body={p.summary.who_is_affected} />
          {p.summary.when_effective && (
            <Section title="언제부터인가요" body={p.summary.when_effective} />
          )}
          {p.summary.key_points && p.summary.key_points.length > 0 && (
            <div className="py-5">
              <p className="doc-eyebrow mb-2.5">핵심 정리</p>
              <ul className="flex flex-col gap-2 text-[0.95rem] leading-relaxed text-ink-soft">
                {p.summary.key_points.map((kp, i) => (
                  <li key={i} className="flex gap-2.5">
                    <span className="mt-px select-none text-seal">•</span>
                    <span>{kp}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}

      <CommentSection policyId={p.id} />
    </article>
  );
}

function Section({ title, body }: { title: string; body: string }) {
  return (
    <div className="py-5">
      <p className="doc-eyebrow mb-2.5">{title}</p>
      <p className="whitespace-pre-wrap text-[0.975rem] leading-[1.75] text-ink-soft">
        {body}
      </p>
    </div>
  );
}
