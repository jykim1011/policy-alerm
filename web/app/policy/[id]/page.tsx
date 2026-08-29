import type { Metadata } from "next";
import { notFound } from "next/navigation";
import Link from "next/link";
import { getPolicy, getPolicyIds, getRelatedPolicies } from "@/lib/policies";
import { fileMeta } from "@/lib/categoryMeta";
import { CategoryIcon, DownloadIcon, ExternalLinkIcon } from "@/components/icons";
import { formatDate } from "@/lib/format";
import { SITE_NAME, SITE_URL } from "@/lib/site";
import { BookmarkButton } from "@/components/BookmarkButton";
import { ShareButton } from "@/components/ShareButton";
import { CommentSection } from "@/components/CommentSection";
import { RelatedPolicies } from "@/components/RelatedPolicies";
import { AppInstallCta } from "@/components/AppInstallCta";
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
      images: [{ url: "/og-default.png", width: 1200, height: 630, alt: SITE_NAME }],
    },
    twitter: { card: "summary_large_image", images: ["/og-default.png"] },
  };
}

export default async function PolicyPage({ params }: Props) {
  const { id } = await params;
  const p = getPolicy(decodeURIComponent(id));
  if (!p) notFound();

  const file = fileMeta(p.file_type);
  const related = getRelatedPolicies(p.id, p.category, p.source);
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
  const faq = p.summary?.faq ?? [];
  const faqLd =
    faq.length > 0
      ? {
          "@context": "https://schema.org",
          "@type": "FAQPage",
          mainEntity: faq.map((f) => ({
            "@type": "Question",
            name: f.question,
            acceptedAnswer: { "@type": "Answer", text: f.answer },
          })),
        }
      : null;

  return (
    <article className="mx-auto max-w-2xl">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />
      {faqLd && (
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: JSON.stringify(faqLd) }}
        />
      )}

      {/* 헤더 카드: 분류·발행일 + 제목 + 부처 + 액션 */}
      <header className="card p-5 sm:p-7">
        <div className="mb-3 flex flex-wrap items-center gap-2">
          <Link
            href={`/category/${encodeURIComponent(p.category)}`}
            className="inline-flex items-center gap-1 rounded-lg bg-seal-soft px-2 py-1 text-xs font-bold text-seal transition hover:bg-seal hover:text-white"
          >
            <CategoryIcon cat={p.category} className="h-3.5 w-3.5" />
            {p.category}
          </Link>
          <time className="text-xs font-medium text-faint">
            발행 {formatDate(p.published_at)}
          </time>
        </div>

        <h1 className="break-keep text-[1.45rem] font-extrabold leading-[1.4] tracking-tight text-ink sm:text-[1.7rem]">
          {p.title}
        </h1>

        {p.source ? (
          <Link
            href={`/source/${encodeURIComponent(p.source)}`}
            aria-label={`${p.source} 정책 모아보기`}
            className="mt-3 inline-block"
          >
            <Seal source={p.source} size="lg" />
          </Link>
        ) : null}

        <div className="mt-5 flex flex-wrap gap-2">
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
            className="inline-flex items-center gap-1.5 rounded-full bg-paper px-4 py-2 text-sm font-semibold text-ink-soft transition hover:bg-rule"
          >
            <ExternalLinkIcon className="h-4 w-4" />
            원문 보기
          </a>
          {p.file_url && file && (
            <a
              href={p.file_url}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 rounded-full bg-paper px-4 py-2 text-sm font-semibold transition hover:bg-rule"
              style={{ color: file.color }}
            >
              <DownloadIcon className="h-4 w-4" />
              {file.label} 첨부
            </a>
          )}
        </div>
      </header>

      {p.summary?.background && (
        <p className="card mt-3 break-keep bg-seal-soft px-5 py-4 text-base leading-relaxed text-ink-soft shadow-none">
          {p.summary.background}
        </p>
      )}

      {p.summary && (
        <div className="card mt-3 flex flex-col divide-y divide-rule px-5 sm:px-7">
          <Section title="무엇이 달라지나요" body={p.summary.what_changed} />
          <Section title="누가 영향을 받나요" body={p.summary.who_is_affected} />
          {p.summary.when_effective && (
            <Section title="언제부터인가요" body={p.summary.when_effective} />
          )}
          {p.summary.key_points && p.summary.key_points.length > 0 && (
            <div className="py-5">
              <p className="mb-2.5 text-[1.02rem] font-bold text-ink">핵심 정리</p>
              <ul className="flex flex-col gap-2 text-base leading-relaxed text-ink-soft">
                {p.summary.key_points.map((kp, i) => (
                  <li key={i} className="flex gap-2.5">
                    <span className="mt-px select-none font-bold text-seal">•</span>
                    <span className="break-keep">{kp}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}

      {/* 자가 체크 */}
      {p.summary?.eligibility && p.summary.eligibility.length > 0 && (
        <div className="card mt-3 p-5 sm:p-7">
          <p className="mb-3 text-[1.02rem] font-bold text-ink">나에게 해당되나요?</p>
          <ul className="flex flex-col gap-2 text-base leading-relaxed text-ink-soft">
            {p.summary.eligibility.map((e, i) => (
              <li key={i} className="flex gap-2.5">
                <span className="mt-0.5 select-none font-bold text-seal">✓</span>
                <span className="break-keep">{e}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* 신청 방법 */}
      {p.summary?.how_to_apply && (
        <div className="card mt-3 p-5 sm:p-7">
          <p className="mb-2 text-[1.02rem] font-bold text-ink">신청 방법·기간</p>
          <p className="whitespace-pre-wrap break-keep text-base leading-[1.75] text-ink-soft">
            {p.summary.how_to_apply}
          </p>
        </div>
      )}

      {/* 용어 풀이 */}
      {p.summary?.glossary && p.summary.glossary.length > 0 && (
        <div className="card mt-3 p-5 sm:p-7">
          <p className="mb-3 text-[1.02rem] font-bold text-ink">용어 풀이</p>
          <dl className="flex flex-col gap-3">
            {p.summary.glossary.map((g, i) => (
              <div key={i}>
                <dt className="text-[0.95rem] font-bold text-ink">{g.term}</dt>
                <dd className="break-keep text-[0.92rem] leading-relaxed text-muted">
                  {g.definition}
                </dd>
              </div>
            ))}
          </dl>
        </div>
      )}

      {/* 자주 묻는 질문 (FAQPage 구조화데이터와 연동) */}
      {faq.length > 0 && (
        <div className="mt-6">
          <p className="mb-3 text-[1.02rem] font-bold text-ink">자주 묻는 질문</p>
          <div className="flex flex-col gap-2.5">
            {faq.map((f, i) => (
              <details key={i} className="card group px-5 py-4">
                <summary className="cursor-pointer list-none break-keep text-[0.95rem] font-bold text-ink [&::-webkit-details-marker]:hidden">
                  <span className="mr-1.5 text-seal">Q.</span>
                  {f.question}
                </summary>
                <p className="mt-2.5 break-keep border-t border-rule pt-2.5 text-[0.93rem] leading-relaxed text-ink-soft">
                  {f.answer}
                </p>
              </details>
            ))}
          </div>
        </div>
      )}

      {/* 출처·면책 고지 — 가공 콘텐츠임을 명시해 신뢰도와 차별성을 더한다. */}
      <p className="mt-4 px-1 text-xs leading-relaxed text-faint">
        이 요약은 {p.source ? `${p.source}의 ` : ""}보도자료를 시민이 이해하기 쉽게
        가공한 것으로 법적 효력이 없습니다. 정확한 신청 자격·기한 등은{" "}
        <a
          href={p.source_url}
          target="_blank"
          rel="noopener noreferrer"
          className="font-semibold text-seal hover:underline"
        >
          원문
        </a>
        을 확인하세요.
      </p>

      <AppInstallCta category={p.category} />

      <RelatedPolicies items={related} />

      <CommentSection policyId={p.id} />
    </article>
  );
}

function Section({ title, body }: { title: string; body: string }) {
  return (
    <div className="py-5">
      <p className="mb-2 text-[1.02rem] font-bold text-ink">{title}</p>
      <p className="whitespace-pre-wrap break-keep text-base leading-[1.75] text-ink-soft">
        {body}
      </p>
    </div>
  );
}
