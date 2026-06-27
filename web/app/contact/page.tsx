import type { Metadata } from "next";
import { SITE_NAME, SITE_URL } from "@/lib/site";

export const metadata: Metadata = {
  title: "문의",
  description: `${SITE_NAME} 문의·오류 제보·개인정보 관련 요청 안내.`,
  alternates: { canonical: `${SITE_URL}/contact/` },
};

export default function ContactPage() {
  return (
    <article className="mx-auto max-w-prose">
      <p className="doc-eyebrow mb-2">문의</p>
      <h1 className="mb-6 text-2xl font-bold text-ink">문의하기</h1>

      <div className="flex flex-col gap-5 text-[0.975rem] leading-[1.8] text-ink-soft">
        <p>
          서비스 이용 중 궁금한 점, 정책 요약 오류 제보, 제휴·광고 문의, 개인정보 관련 요청은
          아래 이메일로 보내주세요. 영업일 기준 신속히 답변드리겠습니다.
        </p>

        <div className="rounded-lg border border-rule bg-paper p-5 text-center">
          <p className="mb-1 text-sm text-muted">이메일</p>
          <a
            href="mailto:jysm8689@gmail.com"
            className="text-lg font-semibold text-seal hover:underline"
          >
            jysm8689@gmail.com
          </a>
        </div>

        <div>
          <h2 className="mb-2 font-bold text-ink">이런 내용을 보내주세요</h2>
          <ul className="flex flex-col gap-1.5 pl-1">
            <li>· 정책 내용·요약 오류 제보</li>
            <li>· 기능 제안 및 서비스 이용 문의</li>
            <li>· 개인정보 열람·정정·삭제 등 권리 행사 요청</li>
            <li>· 광고·제휴 관련 문의</li>
          </ul>
        </div>
      </div>
    </article>
  );
}
