// 정책 공유 — Android 앱 DetailScreen.sharePolicy 와 동일한 형식.
// Web Share API(모바일 네이티브 공유 시트)를 우선 사용하고, 미지원 시 클립보드 복사로 폴백.
"use client";

import { useState } from "react";
import { SITE_NAME, SITE_URL } from "@/lib/site";

interface ShareProps {
  policyId: string;
  title: string;
  source: string;
  publishedAt: string;
  whatChanged?: string | null;
  whenEffective?: string | null;
}

function buildShareText(p: ShareProps): string {
  const lines: string[] = [];
  lines.push(`📋 ${p.title}`);
  lines.push(`🏛 ${p.source} · ${p.publishedAt.slice(0, 10)}`);

  const changed = p.whatChanged?.trim();
  if (changed) {
    const brief = changed.length > 150 ? changed.slice(0, 150).trimEnd() + "…" : changed;
    lines.push("", "🔄 무엇이 바뀌었나", brief);
  }
  const whenEff = p.whenEffective?.trim();
  if (whenEff) {
    lines.push("", "📅 적용 시기", whenEff);
  }

  lines.push("", "──────────", `📲 ${SITE_NAME} · 새 정책을 가장 먼저 받아보세요`);
  return lines.join("\n");
}

export function ShareButton(props: ShareProps) {
  const [copied, setCopied] = useState(false);

  async function share() {
    const url = `${SITE_URL}/policy/${encodeURIComponent(props.policyId)}/`;
    const text = buildShareText(props);

    // Web Share API (모바일·일부 데스크톱) — 네이티브 공유 시트.
    if (typeof navigator !== "undefined" && navigator.share) {
      try {
        await navigator.share({ title: props.title, text, url });
        return;
      } catch (e) {
        // 사용자가 취소(AbortError)하면 조용히 종료. 그 외엔 복사 폴백.
        if (e instanceof DOMException && e.name === "AbortError") return;
      }
    }

    // 폴백: 본문 + 링크를 클립보드에 복사.
    try {
      await navigator.clipboard.writeText(`${text}\n${url}`);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // 클립보드도 막힌 환경 — 무시.
    }
  }

  return (
    <button
      onClick={share}
      className="rounded-md border border-rule-strong bg-paper px-3 py-1.5 text-sm font-medium text-ink-soft hover:border-seal hover:text-seal"
    >
      {copied ? "링크 복사됨 ✓" : "공유하기 ↗"}
    </button>
  );
}
