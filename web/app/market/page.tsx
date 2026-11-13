import type { Metadata } from "next";
import Link from "next/link";
import { getCalendar } from "@/lib/market";
import { formatDate } from "@/lib/format";
import { SITE_URL } from "@/lib/site";
import { ChartIcon } from "@/components/icons";
import type { CalendarEntry, SectorMomentum } from "@/lib/types";

export const metadata: Metadata = {
  title: "시장 — 다가오는 정책 시행 일정",
  description:
    "관련 업종이 있는 정부 정책의 시행 일정과 최근 4주 업종별 정책 흐름을 한눈에 모아 봅니다.",
  alternates: { canonical: `${SITE_URL}/market/` },
};

// 색 규칙: 빨강·파랑은 "영향 방향"(긍정·부정)에만 쓴다. 중요도는 잉크색 배지 하나로만 표시한다.

function policyHref(id: string) {
  return `/policy/${encodeURIComponent(id)}/`;
}

export default function MarketPage() {
  const cal = getCalendar();
  const highCount = cal.upcoming.filter((e) => e.impact === "높음").length;
  const top = cal.momentum[0];
  const maxCount = Math.max(1, ...cal.momentum.map((m) => m.count));
  const weeks = Math.round((cal.momentum_days ?? 28) / 7);

  return (
    <>
      <section className="mb-6 md:mb-8">
        <h1 className="break-keep text-[1.6rem] font-extrabold leading-[1.35] tracking-tight text-ink md:text-3xl">
          정책이 움직이는 업종,
          <br className="md:hidden" /> 한눈에 모아봤어요.
        </h1>
        <p className="mt-2 max-w-prose break-keep text-[0.95rem] leading-relaxed text-muted">
          관련 업종이 있는 정책 중 새로 나왔거나 규모가 큰 것만 골랐어요. 재확인 발표나 계획 단계
          정책은 빠져요.
        </p>
        {cal.updated_at && (
          <p className="mt-3 text-xs font-medium text-faint">갱신 {formatDate(cal.updated_at)}</p>
        )}
      </section>

      {/* 요약 */}
      <div className="mb-8 grid grid-cols-3 gap-3 md:gap-4">
        <Stat label="시행 예정" value={`${cal.upcoming.length}건`} sub={`${cal.upcoming_days ?? 180}일 안`} />
        <Stat label="주요 정책" value={`${highCount}건`} sub="신규·대규모" />
        <Stat
          label="최다 업종"
          value={top ? top.sector : "-"}
          sub={top ? `최근 ${weeks}주 ${top.count}건` : `최근 ${weeks}주`}
        />
      </div>

      {/* 시행 일정 */}
      <SectionLabel>다가오는 시행 일정</SectionLabel>
      {cal.upcoming.length === 0 ? (
        <Empty>앞으로 {cal.upcoming_days ?? 180}일 안에 시행되는 정책이 아직 없어요.</Empty>
      ) : (
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2 md:gap-4">
          {cal.upcoming.map((e) => (
            <UpcomingCard key={e.policy_id} e={e} />
          ))}
        </div>
      )}

      {/* 업종 흐름 */}
      <div className="mt-10 flex flex-wrap items-baseline justify-between gap-2">
        <SectionLabel className="mb-0">최근 {weeks}주 업종별 정책 흐름</SectionLabel>
        <p className="flex items-center gap-3 text-xs font-medium text-faint">
          <Legend cls="text-danger" mark="▲" label="긍정" />
          <Legend cls="text-faint" mark="◆" label="엇갈림" />
          <Legend cls="text-seal" mark="▼" label="부정" />
        </p>
      </div>
      {cal.momentum.length === 0 ? (
        <Empty>최근 업종 관련 정책이 없어요.</Empty>
      ) : (
        <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2 md:grid-cols-3 md:gap-4">
          {cal.momentum.map((m) => (
            <MomentumCard key={m.sector} m={m} maxCount={maxCount} />
          ))}
        </div>
      )}

      <p className="mt-8 break-keep text-xs leading-relaxed text-faint">
        투자 권유가 아닙니다. 정부 보도자료를 바탕으로 정책과 관련될 수 있는 업종을 AI와 편집 기준으로
        정리한 참고 정보이며, 시행 일정은 바뀔 수 있으니 원문을 확인하세요. 투자 판단과 그 책임은
        본인에게 있습니다.
      </p>
    </>
  );
}

function SectionLabel({ children, className = "mb-3" }: { children: React.ReactNode; className?: string }) {
  return <h2 className={`text-sm font-bold text-muted ${className}`}>{children}</h2>;
}

function Empty({ children }: { children: React.ReactNode }) {
  return <p className="card px-5 py-10 text-center text-sm text-muted">{children}</p>;
}

function Legend({ cls, mark, label }: { cls: string; mark: string; label: string }) {
  return (
    <span className="inline-flex items-center gap-1">
      <span className={`text-[0.65rem] ${cls}`}>{mark}</span>
      {label}
    </span>
  );
}

function Stat({ label, value, sub }: { label: string; value: string; sub: string }) {
  return (
    <div className="card px-4 py-4 sm:px-5">
      <p className="text-xs font-semibold text-faint">{label}</p>
      <p className="mt-1 truncate text-[1.3rem] font-extrabold tracking-tight text-ink sm:text-2xl">{value}</p>
      <p className="mt-0.5 truncate text-xs text-muted">{sub}</p>
    </div>
  );
}

/** 시행일 표시: 일자를 알면 "12월 1일", 월만 알면 "12월 중". */
function whenLabel(e: CalendarEntry) {
  const eff = e.effective_date ?? "";
  const month = Number(eff.slice(5, 7));
  return e.date_exact ? `${month}월 ${Number(eff.slice(8, 10))}일` : `${month}월 중`;
}

function UpcomingCard({ e }: { e: CalendarEntry }) {
  const fig = e.figures?.[0];
  return (
    <Link href={policyHref(e.policy_id)} className="card card-hover group flex h-full flex-col p-5">
      <div className="mb-2.5 flex items-center gap-2">
        <span className="inline-flex items-center rounded-lg bg-seal-soft px-2 py-1 text-xs font-bold text-seal">
          {whenLabel(e)} 시행
        </span>
        {e.impact === "높음" && (
          <span className="rounded-lg bg-ink px-2 py-1 text-xs font-bold text-white">주요</span>
        )}
        <span className="ml-auto text-xs font-medium text-faint">
          {[e.novelty, e.stage].filter(Boolean).join(" · ")}
        </span>
      </div>
      <h3 className="mb-4 line-clamp-2 break-keep text-[1.05rem] font-bold leading-[1.45] tracking-[-0.01em] text-ink transition group-hover:text-seal">
        {e.title}
      </h3>
      <div className="mt-auto flex items-end justify-between gap-3">
        <div className="flex flex-wrap gap-1.5">
          {e.sectors.map((s) => (
            <span
              key={s}
              className="inline-flex items-center gap-1 rounded-full bg-paper px-2.5 py-1 text-xs font-semibold text-ink-soft"
            >
              <ChartIcon className="h-3.5 w-3.5 text-faint" />
              {s}
            </span>
          ))}
        </div>
        {fig && (
          <div className="max-w-[55%] text-right">
            <p className="text-[0.7rem] font-semibold text-faint">{fig.label}</p>
            {/* 긴 수치("1057세대 중 284세대")는 한 단계 작게 줄여 카드 밖으로 넘치지 않게 한다. */}
            <p
              className={`break-keep font-extrabold leading-tight tracking-tight text-ink ${
                fig.value.length > 9 ? "text-[0.9rem]" : "text-[1.05rem]"
              }`}
            >
              {fig.value}
            </p>
          </div>
        )}
      </div>
    </Link>
  );
}

function MomentumCard({ m, maxCount }: { m: SectorMomentum; maxCount: number }) {
  const pct = (n: number) => `${(n / m.count) * 100}%`;
  return (
    <div className="card flex flex-col p-5">
      <div className="flex items-baseline justify-between">
        <p className="text-[1.02rem] font-bold text-ink">{m.sector}</p>
        <p className="text-xs font-medium text-faint">
          {m.high > 0 && <span className="mr-1.5 font-bold text-ink">주요 {m.high}</span>}
          긍정 {m.positive} · 부정 {m.negative}
        </p>
      </div>
      <p className="mt-1 text-[1.6rem] font-extrabold leading-none tracking-tight text-ink">
        {m.count}
        <span className="ml-0.5 text-sm font-bold text-muted">건</span>
      </p>
      {/* 막대 길이 = 건수(최다 업종 대비), 색 구간 = 영향 방향 비율 */}
      <div className="mt-3 h-1.5 rounded-full bg-paper" aria-hidden="true">
        <div className="flex h-1.5 overflow-hidden rounded-full" style={{ width: `${(m.count / maxCount) * 100}%` }}>
          <span className="bg-danger" style={{ width: pct(m.positive) }} />
          <span className="bg-rule-strong" style={{ width: pct(m.mixed) }} />
          <span className="bg-seal" style={{ width: pct(m.negative) }} />
        </div>
      </div>
      <ul className="mt-4 flex flex-col gap-2 border-t border-rule pt-3">
        {m.policies.slice(0, VISIBLE).map((p) => (
          <MomentumPolicyRow key={p.policy_id} p={p} />
        ))}
      </ul>
      {/* 건수와 목록이 어긋나지 않게 나머지는 펼쳐 보게 한다(JS 없이 details). */}
      {m.policies.length > VISIBLE && (
        <details className="group mt-2">
          <summary className="cursor-pointer list-none text-xs font-semibold text-seal [&::-webkit-details-marker]:hidden">
            <span className="group-open:hidden">나머지 {m.policies.length - VISIBLE}건 더 보기</span>
            <span className="hidden group-open:inline">접기</span>
          </summary>
          <ul className="mt-2 flex flex-col gap-2">
            {m.policies.slice(VISIBLE).map((p) => (
              <MomentumPolicyRow key={p.policy_id} p={p} />
            ))}
          </ul>
        </details>
      )}
    </div>
  );
}

const VISIBLE = 3;

// 정책별 영향 방향 표시. 색은 막대와 같다(빨강 긍정·회색 엇갈림·파랑 부정).
const DIRECTION_MARK: Record<string, { mark: string; cls: string }> = {
  긍정: { mark: "▲", cls: "text-danger" },
  부정: { mark: "▼", cls: "text-seal" },
  혼재: { mark: "◆", cls: "text-faint" },
};

function MomentumPolicyRow({ p }: { p: SectorMomentum["policies"][number] }) {
  const d = DIRECTION_MARK[p.direction] ?? DIRECTION_MARK["혼재"];
  return (
    <li className="flex items-baseline gap-2 text-[0.85rem] leading-snug">
      <span className={`w-3 shrink-0 text-[0.7rem] ${d.cls}`} title={p.direction} aria-label={p.direction}>
        {d.mark}
      </span>
      <span className="shrink-0 tabular-nums text-faint">{p.published_at.slice(5).replace("-", ".")}</span>
      <Link href={policyHref(p.policy_id)} className="line-clamp-1 break-keep text-ink-soft hover:text-seal">
        {p.title}
      </Link>
    </li>
  );
}

