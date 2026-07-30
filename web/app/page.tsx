import { getAllPolicies } from "@/lib/policies";
import { HomeClient } from "@/components/HomeClient";
import { formatDate } from "@/lib/format";

export default function HomePage() {
  const policies = getAllPolicies();
  const latest = policies[0]?.published_at;

  return (
    <>
      <section className="mb-6 md:mb-8">
        <h1 className="break-keep text-[1.6rem] font-extrabold leading-[1.35] tracking-tight text-ink md:text-3xl">
          새 정책이 나오면,
          <br className="md:hidden" /> 가장 먼저 알려드립니다.
        </h1>
        <p className="mt-2 max-w-prose break-keep text-[0.95rem] leading-relaxed text-muted">
          부동산·청약·대출·복지·고용 등 부처별 보도자료를 한 곳에 모아 핵심만
          요약했습니다.
        </p>
        {latest && (
          <p className="mt-3 text-xs font-medium text-faint">
            최신 발행 {formatDate(latest)}
          </p>
        )}
      </section>

      <HomeClient initial={policies} />
    </>
  );
}
