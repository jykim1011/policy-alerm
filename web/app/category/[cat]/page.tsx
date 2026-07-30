import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getCategoryPolicies } from "@/lib/policies";
import { CATEGORY_LIST, catMeta } from "@/lib/categoryMeta";
import { categoryIntro } from "@/lib/categoryIntro";
import { SITE_URL } from "@/lib/site";
import { PolicyCard } from "@/components/PolicyCard";
import { CategoryIcon } from "@/components/icons";

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

  const intro = categoryIntro(key);
  return (
    <>
      <section className="mb-6">
        <p className="doc-eyebrow mb-2">분류별 보도자료</p>
        <h1 className="flex items-center gap-3 text-[1.6rem] font-extrabold leading-tight tracking-tight text-ink md:text-3xl">
          <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-seal-soft text-seal md:h-12 md:w-12">
            <CategoryIcon cat={key} className="h-6 w-6" />
          </span>
          {meta.full}
        </h1>
        {intro && (
          <p className="mt-3 max-w-prose break-keep text-[0.95rem] leading-[1.75] text-muted">
            {intro}
          </p>
        )}
        <p className="mt-3 text-xs font-medium text-faint">수록 {policies.length}건</p>
      </section>
      <div className="grid grid-cols-1 gap-3 md:grid-cols-2 md:gap-4">
        {policies.map((p) => (
          <PolicyCard key={p.id} p={p} />
        ))}
      </div>
      {policies.length === 0 && (
        <p className="py-16 text-center text-sm text-muted">
          이 분류에는 아직 정책이 없습니다.
        </p>
      )}
    </>
  );
}
