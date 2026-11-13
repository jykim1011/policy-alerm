// 정책 상세 "관련 산업·시장" 카드(서버 컴포넌트, JS 불필요).
// 매수·매도 권유가 아니라 정책과 관련될 수 있는 업종·기업을 정보로 정리한 것이므로
// 항상 고지문을 함께 보여준다. 수익률은 한국 관례대로 상승=빨강, 하락=파랑.

import { SHOW_IMPACT, type PolicyMarket, type PolicyReaction, type ReactionAgg, type SectorStats } from "@/lib/types";
import { getDisclosuresSince, getMinSamples, getPolicyReactions, getSectorStats } from "@/lib/market";

const DIRECTION_LABEL: Record<string, { text: string; cls: string }> = {
  긍정: { text: "긍정적 영향 가능", cls: "bg-[#fff0f1] text-danger" },
  부정: { text: "부정적 영향 가능", cls: "bg-seal-soft text-seal-ink" },
  혼재: { text: "영향 엇갈림", cls: "bg-paper text-muted" },
};

const IMPACT_CLS: Record<string, string> = {
  높음: "bg-danger text-white",
  보통: "bg-ink-soft text-white",
};

/** "2026-11-09" → "2026.11.09", "2027-01" → "2027.01" */
function fmtDate(d: string) {
  return d.replaceAll("-", ".");
}

function quoteUrl(code: string) {
  return `https://finance.naver.com/item/main.naver?code=${code}`;
}

function pct(v: number) {
  const s = (v * 100).toFixed(1);
  return `${v > 0 ? "+" : ""}${s}%`;
}

function pctCls(v: number) {
  return v > 0 ? "text-danger" : v < 0 ? "text-seal-ink" : "text-muted";
}

export function MarketImpact({
  market,
  policyId,
  publishedAt,
}: {
  market: PolicyMarket;
  policyId: string;
  publishedAt: string;
}) {
  // 중요도 "낮음"(재확인·계획 단계 등)과 중요도 판정 전 데이터는 보여주지 않는다.
  if (!market?.sectors?.length || !SHOW_IMPACT.includes(market.impact ?? "")) return null;
  const stats = getSectorStats();
  const minSamples = getMinSamples();
  const reactions = getPolicyReactions(policyId);

  return (
    <section className="card mt-3 p-5 sm:p-7" aria-labelledby="market-heading">
      <div className="mb-1 flex flex-wrap items-center gap-2">
        <h2 id="market-heading" className="text-[1.02rem] font-bold text-ink">
          📈 관련 산업·시장
        </h2>
        <span className={`rounded-md px-1.5 py-0.5 text-[0.7rem] font-bold ${IMPACT_CLS[market.impact!]}`}>
          중요도 {market.impact}
        </span>
      </div>

      {/* 핵심 사실: 신규 여부 · 단계 · 시행 시점 · 정량 수치 */}
      <dl className="mt-2.5 grid grid-cols-2 gap-2 sm:grid-cols-4">
        {market.novelty && <Fact label="새 정보" value={market.novelty} />}
        {market.stage && <Fact label="단계" value={market.stage} />}
        {market.effective_date && <Fact label="시행" value={fmtDate(market.effective_date)} />}
        {(market.figures ?? []).map((f) => (
          <Fact key={f.label + f.value} label={f.label} value={f.value} />
        ))}
      </dl>

      {market.investor_note && (
        <p className="mt-3 break-keep text-[0.93rem] leading-relaxed text-ink-soft">
          {market.investor_note}
        </p>
      )}

      <div className="mt-3 flex flex-col divide-y divide-rule">
        {market.sectors.map((s) => (
          <SectorBlock
            key={s.sector}
            sector={s}
            reaction={reactions[s.sector]}
            stat={stats[s.sector]}
            minSamples={minSamples}
            publishedAt={publishedAt}
          />
        ))}
      </div>

      <p className="mt-4 rounded-xl bg-paper px-3.5 py-3 text-[0.75rem] leading-relaxed text-faint">
        중요도는 새 정보인지(신규·확대), 규모(1조원·1만 호 이상), 확정 단계(의결·시행)로 계산해요.
        투자 권유가 아닙니다. 정책과 관련될 수 있는 업종·기업을 AI와 편집 기준으로 정리한
        참고 정보이며, 과거 수익률은 미래 수익을 보장하지 않습니다. 투자 판단과 그 책임은
        본인에게 있습니다.
      </p>
    </section>
  );
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl bg-paper px-3 py-2">
      <dt className="text-[0.7rem] font-semibold text-faint">{label}</dt>
      <dd className="break-keep text-[0.88rem] font-bold text-ink">{value}</dd>
    </div>
  );
}

function SectorBlock({
  sector,
  reaction,
  stat,
  minSamples,
  publishedAt,
}: {
  sector: PolicyMarket["sectors"][number];
  reaction?: PolicyReaction;
  stat?: SectorStats;
  minSamples: number;
  publishedAt: string;
}) {
  const dir = DIRECTION_LABEL[sector.direction] ?? DIRECTION_LABEL["혼재"];
  // 같은 방향으로 분류된 과거 정책 표본이 충분하면 그 집계를, 아니면 업종 전체 집계를 쓴다.
  const sameDir = stat?.by_direction?.[sector.direction];
  const agg: ReactionAgg | undefined =
    sameDir && sameDir.count >= minSamples ? sameDir : stat && stat.count >= minSamples ? stat : undefined;
  const aggLabel = agg === sameDir ? `'${sector.direction}'으로 분류된 ${sector.sector} 정책` : `${sector.sector} 관련 정책`;
  const disclosures = sector.stocks
    .flatMap((t) => getDisclosuresSince(t.code, publishedAt).map((d) => ({ ...d, name: t.name })))
    .sort((a, b) => b.date.localeCompare(a.date))
    .slice(0, 5);

  return (
    <div className="py-4 first:pt-1">
      <div className="mb-1.5 flex flex-wrap items-center gap-2">
        <span className="text-[0.98rem] font-bold text-ink">{sector.sector}</span>
        <span className={`rounded-md px-1.5 py-0.5 text-[0.72rem] font-bold ${dir.cls}`}>{dir.text}</span>
      </div>
      <p className="break-keep text-[0.92rem] leading-relaxed text-ink-soft">{sector.reason}</p>

      {(sector.etfs.length > 0 || sector.stocks.length > 0) && (
        <div className="mt-2.5 flex flex-wrap gap-1.5">
          {sector.etfs.map((t) => (
            <a
              key={t.code}
              href={quoteUrl(t.code)}
              target="_blank"
              rel="noopener noreferrer nofollow"
              className="rounded-full border border-seal/30 bg-seal-soft px-2.5 py-1 text-[0.78rem] font-semibold text-seal-ink transition hover:border-seal"
            >
              ETF · {t.name}
            </a>
          ))}
          {sector.stocks.map((t) => (
            <a
              key={t.code}
              href={quoteUrl(t.code)}
              target="_blank"
              rel="noopener noreferrer nofollow"
              className="rounded-full border border-rule bg-surface px-2.5 py-1 text-[0.78rem] font-medium text-ink-soft transition hover:border-rule-strong"
            >
              {t.name}
            </a>
          ))}
        </div>
      )}

      {reaction?.d5 !== undefined && (
        <p className="mt-2.5 text-[0.82rem] text-muted">
          이 정책 발표 후 5거래일 업종 움직임{" "}
          <b className={pctCls(reaction.d5)}>{pct(reaction.d5)}</b>
          {reaction.x5 !== undefined && (
            <>
              {" "}(KOSPI200 대비 <b className={pctCls(reaction.x5)}>{pct(reaction.x5)}</b>)
            </>
          )}
        </p>
      )}

      {agg && agg.avg_d5 != null && (
        <div className="mt-2.5 rounded-xl bg-paper px-3.5 py-2.5 text-[0.82rem] leading-relaxed text-muted">
          과거 {aggLabel} <b className="text-ink">{agg.count}건</b> 발표 후 5거래일 평균{" "}
          <b className={pctCls(agg.avg_d5)}>{pct(agg.avg_d5)}</b>
          {agg.avg_x5 != null && (
            <>
              {" "}· 시장 대비 <b className={pctCls(agg.avg_x5)}>{pct(agg.avg_x5)}</b>
            </>
          )}
          {agg.up_ratio_d5 != null && <> · 상승 비율 {Math.round(agg.up_ratio_d5 * 100)}%</>}
        </div>
      )}

      {disclosures.length > 0 && (
        <details className="mt-2.5">
          <summary className="cursor-pointer list-none text-[0.8rem] font-semibold text-seal [&::-webkit-details-marker]:hidden">
            발표 이후 관련 기업 공시 {disclosures.length}건 ▾
          </summary>
          <ul className="mt-2 flex flex-col gap-1.5">
            {disclosures.map((d) => (
              <li key={d.url} className="text-[0.8rem] leading-snug">
                <a href={d.url} target="_blank" rel="noopener noreferrer nofollow" className="text-ink-soft hover:underline">
                  <span className="font-semibold">{d.name}</span> · {d.title}
                </a>{" "}
                <span className="text-faint">{d.date.slice(5).replace("-", ".")}</span>
              </li>
            ))}
          </ul>
        </details>
      )}
    </div>
  );
}
