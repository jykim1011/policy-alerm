// 모바일 하단 탭바 — 앱과 같은 4탭(홈·보관함·알림·설정). md 이상에서는 숨김.
"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { BellIcon, BookmarkIcon, GearIcon, HomeIcon } from "./icons";

const TABS = [
  { href: "/", label: "홈", Icon: HomeIcon },
  { href: "/bookmarks", label: "보관함", Icon: BookmarkIcon },
  { href: "/notifications", label: "알림", Icon: BellIcon },
  { href: "/settings", label: "설정", Icon: GearIcon },
];

export function MobileTabBar() {
  const pathname = usePathname();

  return (
    <nav
      aria-label="주요 메뉴"
      className="fixed inset-x-0 bottom-0 z-20 border-t border-rule bg-surface/95 pb-[env(safe-area-inset-bottom)] backdrop-blur-md md:hidden"
    >
      <div className="mx-auto flex max-w-md">
        {TABS.map(({ href, label, Icon }) => {
          const active = href === "/" ? pathname === "/" : pathname?.startsWith(href);
          return (
            <Link
              key={href}
              href={href}
              aria-current={active ? "page" : undefined}
              className={`flex flex-1 flex-col items-center gap-0.5 py-2 text-[0.65rem] transition ${
                active ? "font-bold text-seal" : "font-medium text-faint"
              }`}
            >
              <Icon className="h-6 w-6" strokeWidth={active ? 2.1 : 1.7} />
              {label}
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
