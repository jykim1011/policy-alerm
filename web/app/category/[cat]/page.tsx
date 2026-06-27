import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getCategoryPolicies } from "@/lib/policies";
import { CATEGORY_LIST, catMeta } from "@/lib/categoryMeta";
import { SITE_URL } from "@/lib/site";
import { PolicyCard } from "@/components/PolicyCard";

export const dynamicParams = false;

export function generateStaticParams() {
  // "전체"는 홈(/)이 담당하므로 제외.
  return CATEGORY_LIST.filter((c) => c.key !== "전체").map((c) => ({ cat: c.key }));
}

type Props = { params: Promise<{ cat: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { cat } = await params;
  const key = decodeURIComponent(cat);
  const meta = catMeta(key);
  return {
    title: `${meta.full} 정책`,
    description: `${meta.full} 관련 정부 정책·지원금 보도자료 모음.`,
    alternates: { canonical: `${SITE_URL}/category/${encodeURIComponent(key)}/` },
  };
}

export default async function CategoryPage({ params }: Props) {
  const { cat } = await params;
  const key = decodeURIComponent(cat);
  if (!CATEGORY_LIST.some((c) => c.key === key)) notFound();
  const meta = catMeta(key);
  const policies = getCategoryPolicies(key);

  return (
    <>
      <section className="mb-6 border-b border-rule pb-4">
        <p className="doc-eyebrow mb-2">분류별 보도자료</p>
        <h1 className="font-serif text-3xl font-bold leading-tight text-ink">
          {meta.emoji} {meta.full}
        </h1>
        <p className="mt-2 font-mono text-xs text-faint">수록 {policies.length}건</p>
      </section>
      <div>
        {policies.map((p) => (
          <PolicyCard key={p.id} p={p} />
        ))}
        {policies.length === 0 && (
          <p className="py-16 text-center text-sm text-muted">
            이 분류에는 아직 정책이 없습니다.
          </p>
        )}
      </div>
    </>
  );
}
