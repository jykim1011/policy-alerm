import type { MetadataRoute } from "next";
import { getArchivedPolicies, getAllSources } from "@/lib/policies";
import { CATEGORY_LIST } from "@/lib/categoryMeta";
import { SITE_URL } from "@/lib/site";

export const dynamic = "force-static";

export default function sitemap(): MetadataRoute.Sitemap {
  const policies = getArchivedPolicies();

  const policyUrls: MetadataRoute.Sitemap = policies.map((p) => ({
    url: `${SITE_URL}/policy/${encodeURIComponent(p.id)}/`,
    lastModified: new Date(p.published_at),
    changeFrequency: "monthly",
    priority: 0.7,
  }));

  const categoryUrls: MetadataRoute.Sitemap = CATEGORY_LIST.filter(
    (c) => c.key !== "전체",
  ).map((c) => ({
    url: `${SITE_URL}/category/${encodeURIComponent(c.key)}/`,
    changeFrequency: "daily",
    priority: 0.6,
  }));

  const sourceUrls: MetadataRoute.Sitemap = getAllSources().map((s) => ({
    url: `${SITE_URL}/source/${encodeURIComponent(s.name)}/`,
    changeFrequency: "weekly",
    priority: 0.5,
  }));

  const staticUrls: MetadataRoute.Sitemap = [
    "/about",
    "/privacy",
    "/contact",
  ].map((p) => ({
    url: `${SITE_URL}${p}/`,
    changeFrequency: "yearly",
    priority: 0.3,
  }));

  return [
    { url: `${SITE_URL}/`, changeFrequency: "daily", priority: 1 },
    ...staticUrls,
    ...categoryUrls,
    ...sourceUrls,
    ...policyUrls,
  ];
}
