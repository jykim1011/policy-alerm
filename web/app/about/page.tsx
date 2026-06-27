import type { Metadata } from "next";
import { SITE_NAME, SITE_URL } from "@/lib/site";
import { GooglePlayBadge } from "@/components/GooglePlayBadge";

export const metadata: Metadata = {
  title: "서비스 소개",
  description: `${SITE_NAME}은 부동산·청약·대출·복지·고용 등 정부 정책 보도자료를 모아 핵심만 요약해 제공하는 서비스입니다.`,
  alternates: { canonical: `${SITE_URL}/about/` },
};

export default function AboutPage() {
  return (
    <article className="mx-auto max-w-prose">
      <p className="doc-eyebrow mb-2">서비스 소개</p>
      <h1 className="mb-6 text-2xl font-bold text-ink">정책알람이란</h1>

      <div className="flex flex-col gap-5 text-[0.975rem] leading-[1.8] text-ink-soft">
        <p>
          <strong className="text-ink">정책알람</strong>은 부동산·청약·대출·복지·고용·창업·육아
          등 일상에 직접 영향을 주는 <strong className="text-ink">정부 정책 보도자료</strong>를
          한 곳에 모아, 핵심만 알기 쉽게 요약해 전하는 서비스입니다. 흩어져 있는 부처별 발표를
          매일 자동으로 수집해 분야별로 정리하고, 새로운 정책이 나오면 가장 먼저 알려드립니다.
        </p>

        <div>
          <h2 className="mb-2 font-bold text-ink">이렇게 만듭니다</h2>
          <ul className="flex flex-col gap-1.5 pl-1">
            <li>· 정책브리핑·공공데이터포털 등 <strong className="text-ink">정부 공개자료</strong>를 수집합니다.</li>
            <li>· 각 보도자료를 <strong className="text-ink">무엇이 바뀌는지·누가 영향을 받는지·언제부터인지</strong>로 요약·재구성합니다.</li>
            <li>· 부동산·복지·고용 등 분야와 주관부처별로 분류해 찾기 쉽게 정리합니다.</li>
            <li>· 이용자들이 정책에 대한 의견을 나눌 수 있습니다.</li>
          </ul>
        </div>

        <div>
          <h2 className="mb-2 font-bold text-ink">참고해 주세요</h2>
          <p>
            제공되는 정책 정보는 정부 공개자료를 수집·요약·가공한 것으로 <strong className="text-ink">법적
            효력이 없습니다</strong>. 정확한 내용과 신청 자격·기한 등은 반드시 각 부처의 원문을 확인하시기
            바랍니다. 요약 과정에서 오류가 있을 수 있으며, 발견 시 알려주시면 신속히 반영하겠습니다.
          </p>
        </div>

        <div>
          <h2 className="mb-3 font-bold text-ink">앱으로도 만나보세요</h2>
          <p className="mb-3">
            안드로이드 앱에서는 관심 분야를 구독하면 새 정책 알림을 푸시로 가장 먼저 받아볼 수 있습니다.
          </p>
          <GooglePlayBadge size="md" />
        </div>

        <p className="text-sm text-muted">
          문의: <a href="mailto:jysm8689@gmail.com" className="text-seal hover:underline">jysm8689@gmail.com</a>
        </p>
      </div>
    </article>
  );
}
