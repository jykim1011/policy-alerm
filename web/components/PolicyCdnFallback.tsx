// 404 폴백 — 경로가 /policy/{id} 면 CDN 에서 정책 JSON 을 받아 상세를 렌더한다.
// 새 정책이 CDN 에 먼저 올라오고 웹이 아직 재빌드되지 않은 공백 시간에도
// 링크가 깨지지 않게 한다. 그 외 경로는 일반 404 안내를 보여준다.
"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import type { PolicyDetail } from "@/lib/types";
import { fileMeta } from "@/lib/categoryMeta";
import { formatDate, sanitizeSummary } from "@/lib/format";
import { CategoryIcon, DownloadIcon, ExternalLinkIcon } from "./icons";
import { CDN_BASE } from "@/lib/site";
import { BookmarkButton } from "./BookmarkButton";
import { ShareButton } from "./ShareButton";
import { CommentSection } from "./CommentSection";
import { Seal } from "./Seal";

function policyIdFromPath(pathname: string): string | null {
  const m = pathname.match(/^\/policy\/([^/]+)\/?$/);
  return m ? decodeURIComponent(m[1]) : null;
}

export function PolicyCdnFallback() {
  // "checking" 동안은 빈 화면 대신 로딩을 보여 404 플래시를 막는다.
  const [state, setState] = useState<"checking" | "notfound" | "found">(
    "checking",
  );
  const [policy, setPolicy] = useState<PolicyDetail | null>(null);

  useEffect(() => {
    const id = policyIdFromPath(window.location.pathname);
    if (!id) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setState("notfound");
      return;
    }
    let alive = true;
    fetch(`${CDN_BASE}/policies/${encodeURIComponent(id)}.json`)
      .then((r) => (r.ok ? r.json() : null))
      .then((data: PolicyDetail | null) => {
        if (!alive) return;
        if (data?.id) {
          if (data.summary) sanitizeSummary(data.summary);
          setPolicy(data);
          setState("found");
        } else {
          setState("notfound");
        }
      })
      .catch(() => {
        if (alive) setState("notfound");
      });
    return () => {
      alive = false;
    };
  }, []);

  if (state === "checking") {
    return (
      <p className="py-24 text-center text-xs font-medium text-faint">
        불러오는 중…
      </p>
    );
  }

  if (state === "notfound" || !policy) {
    return (
      <div className="card mx-auto max-w-2xl px-6 py-16 text-center">
        <p className="text-4xl font-extrabold tracking-tight text-ink">404</p>
        <p className="mt-3 break-keep text-sm text-muted">
          페이지를 찾을 수 없습니다. 주소가 바뀌었거나 삭제된 정책일 수 있어요.
        </p>
        <Link
          href="/"
          className="mt-6 inline-block rounded-full bg-seal px-6 py-2.5 text-sm font-bold text-white transition hover:bg-seal-ink"
        >
          홈으로 가기
        </Link>
      </div>
    );
  }

  const p = policy;
  const s = p.summary;
  const file = fileMeta(p.file_type);

  return (
    <article className="mx-auto max-w-2xl">
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
          <span className="mt-3 inline-block">
            <Seal source={p.source} size="lg" />
          </span>
        ) : null}

        <div className="mt-5 flex flex-wrap gap-2">
          <BookmarkButton policyId={p.id} />
          <ShareButton
            policyId={p.id}
            title={p.title}
            source={p.source}
            publishedAt={p.published_at}
            whatChanged={s?.what_changed}
            whenEffective={s?.when_effective}
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

      {s?.background && (
        <p className="card mt-3 break-keep bg-seal-soft px-5 py-4 text-[0.95rem] leading-relaxed text-ink-soft shadow-none">
          {s.background}
        </p>
      )}

      {s && (
        <div className="card mt-3 flex flex-col divide-y divide-rule px-5 sm:px-7">
          <Section title="무엇이 달라지나요" body={s.what_changed} />
          <Section title="누가 영향을 받나요" body={s.who_is_affected} />
          {s.when_effective && (
            <Section title="언제부터인가요" body={s.when_effective} />
          )}
          {s.key_points && s.key_points.length > 0 && (
            <div className="py-5">
              <p className="mb-2.5 text-[1.02rem] font-bold text-ink">핵심 정리</p>
              <ul className="flex flex-col gap-2 text-[0.95rem] leading-relaxed text-ink-soft">
                {s.key_points.map((kp, i) => (
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

      {s?.eligibility && s.eligibility.length > 0 && (
        <div className="card mt-3 p-5 sm:p-7">
          <p className="mb-3 text-[1.02rem] font-bold text-ink">나에게 해당되나요?</p>
          <ul className="flex flex-col gap-2 text-[0.95rem] leading-relaxed text-ink-soft">
            {s.eligibility.map((e, i) => (
              <li key={i} className="flex gap-2.5">
                <span className="mt-0.5 select-none font-bold text-seal">✓</span>
                <span className="break-keep">{e}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {s?.how_to_apply && (
        <div className="card mt-3 p-5 sm:p-7">
          <p className="mb-2 text-[1.02rem] font-bold text-ink">신청 방법·기간</p>
          <p className="whitespace-pre-wrap break-keep text-[0.95rem] leading-[1.75] text-ink-soft">
            {s.how_to_apply}
          </p>
        </div>
      )}

      {s?.glossary && s.glossary.length > 0 && (
        <div className="card mt-3 p-5 sm:p-7">
          <p className="mb-3 text-[1.02rem] font-bold text-ink">용어 풀이</p>
          <dl className="flex flex-col gap-3">
            {s.glossary.map((g, i) => (
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

      {s?.faq && s.faq.length > 0 && (
        <div className="mt-6">
          <p className="mb-3 text-[1.02rem] font-bold text-ink">자주 묻는 질문</p>
          <div className="flex flex-col gap-2.5">
            {s.faq.map((f, i) => (
              <details key={i} className="card px-5 py-4">
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

      <CommentSection policyId={p.id} />
    </article>
  );
}

function Section({ title, body }: { title: string; body: string }) {
  return (
    <div className="py-5">
      <p className="mb-2 text-[1.02rem] font-bold text-ink">{title}</p>
      <p className="whitespace-pre-wrap break-keep text-[0.95rem] leading-[1.75] text-ink-soft">
        {body}
      </p>
    </div>
  );
}
