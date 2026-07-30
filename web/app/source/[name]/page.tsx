import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getAllSources, getSourcePolicies } from "@/lib/policies";
import { SITE_NAME, SITE_URL } from "@/lib/site";
import { PolicyCard } from "@/components/PolicyCard";
import { BuildingIcon } from "@/components/icons";

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
      <section className="mb-6">
        <p className="doc-eyebrow mb-2">주관부처별 보도자료</p>
        <h1 className="flex items-center gap-3 text-[1.6rem] font-extrabold leading-tight tracking-tight text-ink md:text-3xl">
          <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-seal-soft text-seal md:h-12 md:w-12">
            <BuildingIcon className="h-6 w-6" />
          </span>
          {source}
        </h1>
        <p className="mt-3 max-w-prose break-keep text-[0.95rem] leading-[1.75] text-muted">
          {source}이(가) 발표한 정책 보도자료를 모았습니다. 발표 시점과 적용 시기를 함께
          확인하고, 자세한 내용은 각 정책의 원문을 참고하세요.
        </p>
        <p className="mt-3 text-xs font-medium text-faint">수록 {policies.length}건</p>
      </section>
      <div className="grid grid-cols-1 gap-3 md:grid-cols-2 md:gap-4">
        {policies.map((p) => (
          <PolicyCard key={p.id} p={p} />
        ))}
      </div>
    </>
  );
}
