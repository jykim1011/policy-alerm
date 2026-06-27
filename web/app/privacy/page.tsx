import type { Metadata } from "next";
import { SITE_NAME, SITE_URL } from "@/lib/site";

export const metadata: Metadata = {
  title: "개인정보처리방침",
  description: `${SITE_NAME} 웹사이트의 개인정보 수집·이용, 쿠키 및 광고, 제3자 처리위탁에 관한 안내.`,
  alternates: { canonical: `${SITE_URL}/privacy/` },
};

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section>
      <h2 className="mb-2 mt-6 text-base font-bold text-ink">{title}</h2>
      <div className="flex flex-col gap-2">{children}</div>
    </section>
  );
}

export default function PrivacyPage() {
  return (
    <article className="mx-auto max-w-prose text-[0.95rem] leading-[1.8] text-ink-soft">
      <p className="doc-eyebrow mb-2">개인정보처리방침</p>
      <h1 className="mb-4 text-2xl font-bold text-ink">개인정보처리방침</h1>

      <p className="text-sm text-muted">
        서비스명: 정책알람 (Policy Alarm) · 운영자: 개인 개발자 (jysm8689@gmail.com) · 시행일:
        2026년 6월 27일
      </p>
      <p className="mt-3">
        정책알람(이하 “서비스”)은 이용자의 개인정보를 중요하게 여기며, 개인정보 보호법 등 관련
        법령을 준수합니다. 본 방침은 웹사이트(policy-alerm.web.app) 이용에 적용됩니다.
      </p>

      <Section title="1. 수집하는 개인정보 항목과 목적">
        <ul className="flex flex-col gap-1.5">
          <li>· <strong className="text-ink">Google 계정 정보(이메일, 이름, 계정 고유 ID)</strong> — Google 소셜 로그인 및 이용자 식별. 회원 탈퇴 시 삭제.</li>
          <li>· <strong className="text-ink">닉네임, 작성한 댓글, 북마크, 받은 알림·읽음 상태</strong> — 댓글·보관함·알림 기능 제공. 이용자가 삭제하거나 탈퇴 시 삭제.</li>
          <li>· <strong className="text-ink">접속 로그·기기/브라우저 정보·쿠키·IP 주소</strong> — 서비스 운영, 보안, 통계, 광고 게재·측정을 위해 자동 수집될 수 있습니다.</li>
        </ul>
        <p>
          로그인하지 않아도 정책 열람·검색은 가능하며, 이 경우 계정 정보는 수집하지 않습니다.
        </p>
      </Section>

      <Section title="2. 쿠키 및 광고">
        <p>
          본 서비스는 향후 <strong className="text-ink">Google AdSense</strong> 등 제3자 광고를 게재할 수
          있습니다. 이 과정에서 Google을 포함한 제3자 공급업체는 쿠키(예: DoubleClick 쿠키)를 사용해
          이용자의 본 사이트 및 다른 사이트 방문 기록을 바탕으로 맞춤형 광고를 제공할 수 있습니다.
        </p>
        <ul className="flex flex-col gap-1.5">
          <li>· Google의 광고 쿠키 사용으로 Google과 광고 파트너는 이용자에게 맞춤 광고를 게재할 수 있습니다.</li>
          <li>· 이용자는 <a className="text-seal hover:underline" href="https://www.google.com/settings/ads" target="_blank" rel="noopener noreferrer">Google 광고 설정</a>에서 맞춤 광고를 해제할 수 있습니다.</li>
          <li>· <a className="text-seal hover:underline" href="https://www.aboutads.info/choices/" target="_blank" rel="noopener noreferrer">www.aboutads.info</a>에서 제3자 공급업체의 맞춤 광고를 일괄 해제할 수 있습니다.</li>
          <li>· 브라우저 설정에서 쿠키를 차단할 수 있으나, 일부 기능이 제한될 수 있습니다.</li>
        </ul>
      </Section>

      <Section title="3. 개인정보의 제3자 처리위탁">
        <p>서비스는 이용자의 개인정보를 판매하지 않으며, 운영을 위해 아래에 처리를 위탁합니다.</p>
        <ul className="flex flex-col gap-1.5">
          <li>· <strong className="text-ink">Google LLC — Firebase</strong> (Authentication, Firestore): 계정 정보·닉네임·댓글·북마크·알림 저장 및 인증 인프라.</li>
          <li>· <strong className="text-ink">Google LLC — Google AdSense</strong>: 광고 게재 및 성과 측정(쿠키·기기 정보·IP).</li>
        </ul>
        <p>
          자세한 내용은{" "}
          <a className="text-seal hover:underline" href="https://policies.google.com/privacy" target="_blank" rel="noopener noreferrer">Google 개인정보처리방침</a>{" "}및{" "}
          <a className="text-seal hover:underline" href="https://policies.google.com/technologies/partner-sites" target="_blank" rel="noopener noreferrer">Google이 파트너 사이트에서 데이터를 사용하는 방법</a>을 참고하세요.
        </p>
      </Section>

      <Section title="4. 보유 및 이용 기간">
        <p>
          수집된 개인정보는 회원 탈퇴(계정·데이터 삭제) 요청 시 지체 없이 삭제합니다. 단, 관계
          법령에 따라 보존이 필요한 경우 해당 기간 동안 보관합니다.
        </p>
      </Section>

      <Section title="5. 이용자의 권리">
        <ul className="flex flex-col gap-1.5">
          <li>· 개인정보 열람·정정·삭제 및 처리 정지 요청</li>
          <li>· 로그아웃으로 계정 연결 해제, 계정·데이터 삭제 요청</li>
          <li>· 브라우저·Google 광고 설정을 통한 쿠키·맞춤 광고 거부</li>
        </ul>
        <p>권리 행사는 아래 문의처로 요청하시면 관련 법령상 기한 내 처리합니다.</p>
      </Section>

      <Section title="6. 아동의 개인정보">
        <p>
          서비스는 아동을 주 대상으로 하지 않으며, 만 14세 미만 아동의 개인정보를 알면서 수집하지
          않습니다. 인지한 경우 지체 없이 삭제합니다.
        </p>
      </Section>

      <Section title="7. 정책 콘텐츠에 관한 고지">
        <p>
          제공되는 정책 정보는 정부 공개자료(정책브리핑·공공데이터포털 등)를 수집·요약·가공한
          것으로 법적 효력이 없습니다. 정확한 내용은 각 부처의 원문을 확인하시기 바랍니다.
        </p>
      </Section>

      <Section title="8. 방침의 변경 및 문의">
        <p>
          본 방침은 법령·서비스 변경 시 개정될 수 있으며, 변경 시 본 페이지를 통해 공지합니다.
          개인정보 관련 문의:{" "}
          <a className="text-seal hover:underline" href="mailto:jysm8689@gmail.com">jysm8689@gmail.com</a>
        </p>
      </Section>

      <p className="mt-8 border-t border-rule pt-4 text-xs text-faint">
        © 2026 {SITE_NAME}. 문의: jysm8689@gmail.com
      </p>
    </article>
  );
}
