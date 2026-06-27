import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getAllSources, getSourcePolicies } from "@/lib/policies";
import { SITE_NAME, SITE_URL } from "@/lib/site";
import { PolicyCard } from "@/components/PolicyCard";

export const dynamicParams = false;

export function generateStaticParams() {
  return getAllSources().map((s) => ({ name: s.name }));
}

type Props = { params: Promise<{ name: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { name } = await params;
  const source = decodeURIComponent(name);
  return {
    title: `${source} 정책`,
    description: `${source}이(가) 발표한 정부 정책·지원금 보도자료를 한눈에 모았습니다. ${SITE_NAME}에서 핵심 요약과 함께 확인하세요.`,
    alternates: { canonical: `${SITE_URL}/source/${encodeURIComponent(source)}/` },
  };
}

export default async function SourcePage({ params }: Props) {
  const { name } = await params;
  const source = decodeURIComponent(name);
  const policies = getSourcePolicies(source);
  if (policies.length === 0) notFound();

  return (
    <>
      <section className="mb-6 border-b border-rule pb-5">
        <p className="doc-eyebrow mb-2">주관부처별 보도자료</p>
        <h1 className="font-serif text-3xl font-bold leading-tight text-ink">
          🏛 {source}
        </h1>
        <p className="mt-3 max-w-prose break-keep text-[0.95rem] leading-[1.75] text-ink-soft">
          {source}이(가) 발표한 정책 보도자료를 모았습니다. 발표 시점과 적용 시기를 함께
          확인하고, 자세한 내용은 각 정책의 원문을 참고하세요.
        </p>
        <p className="mt-3 font-mono text-xs text-faint">수록 {policies.length}건</p>
      </section>
      <div>
        {policies.map((p) => (
          <PolicyCard key={p.id} p={p} />
        ))}
      </div>
    </>
  );
}
