import type { MetadataRoute } from "next";
import { SITE_DESC, SITE_NAME } from "@/lib/site";

export const dynamic = "force-static";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: `${SITE_NAME} — 정부 정책·지원금 알리미`,
    short_name: SITE_NAME,
    description: SITE_DESC,
    start_url: "/",
    display: "standalone",
    background_color: "#f4f6fb",
    theme_color: "#1d4ed8",
    lang: "ko",
    icons: [
      { src: "/icon-192.png", sizes: "192x192", type: "image/png" },
      { src: "/icon-512.png", sizes: "512x512", type: "image/png" },
      {
        src: "/icon-512.png",
        sizes: "512x512",
        type: "image/png",
        purpose: "maskable",
      },
    ],
  };
}
