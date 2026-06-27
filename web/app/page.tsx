import { getAllPolicies } from "@/lib/policies";
import { HomeClient } from "@/components/HomeClient";
import { formatDate } from "@/lib/format";

export default function HomePage() {
  const policies = getAllPolicies();
  const latest = policies[0]?.published_at;

  return (
    <>
      <section className="mb-8">
        <p className="doc-eyebrow mb-3">정부 정책 보도자료</p>
        <h1 className="font-serif text-3xl font-bold leading-tight text-ink">
          새 정책이 나오면,
          <br />
          가장 먼저 알려드립니다.
        </h1>
        <p className="mt-3 max-w-prose text-sm leading-relaxed text-muted">
          부동산·청약·대출·복지·고용 등 부처별 보도자료를 한 곳에 모아
          핵심만 요약했습니다.
        </p>
        {latest && (
          <p className="mt-4 font-mono text-xs text-faint">
            최신 발행 {formatDate(latest)}
          </p>
        )}
      </section>

      <HomeClient initial={policies} />
    </>
  );
}
