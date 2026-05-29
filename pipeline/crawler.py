import json
import re
from datetime import datetime
from typing import Optional
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup

from pipeline.models import RawPolicy

SEEN_FILE = "pipeline/seen_policies.json"
HEADERS = {"User-Agent": "Mozilla/5.0 (compatible; PolicyBot/1.0)"}


def load_seen(path: str = SEEN_FILE) -> set[str]:
    with open(path, encoding="utf-8") as f:
        return set(json.load(f)["seen"])


def save_seen(seen: set[str], path: str = SEEN_FILE) -> None:
    with open(path, "w", encoding="utf-8") as f:
        json.dump({"seen": list(seen)}, f, ensure_ascii=False)


def is_new_policy(url_hash: str, seen: set[str]) -> bool:
    return url_hash not in seen


class MolitCrawler:
    """국토교통부 보도자료 크롤러"""

    BASE_URL = "https://www.molit.go.kr"
    LIST_URL = "https://www.molit.go.kr/USR/NEWS/m_71/dtl.jsp?id=95080455"
    CATEGORY = "부동산"
    SOURCE = "국토교통부"

    def fetch(self) -> list[RawPolicy]:
        resp = requests.get(self.LIST_URL, headers=HEADERS, timeout=20)
        resp.raise_for_status()
        soup = BeautifulSoup(resp.text, "lxml")

        results = []
        for row in soup.select("table.board_list tbody tr"):
            cols = row.select("td")
            if len(cols) < 4:
                continue

            link_tag = row.select_one("td.title a")
            if not link_tag:
                continue

            title = link_tag.get_text(strip=True)
            href = link_tag.get("href", "")
            url = urljoin(self.BASE_URL, href)
            date_text = cols[-1].get_text(strip=True)

            detail = self._fetch_detail(url)
            raw = RawPolicy(
                url=url,
                title=title,
                source=self.SOURCE,
                published_at=self._parse_date(date_text),
                file_url=detail.get("file_url"),
                file_type=detail.get("file_type"),
                html_content=detail.get("html_content", ""),
            )
            results.append(raw)

        return results

    def _fetch_detail(self, url: str) -> dict:
        try:
            resp = requests.get(url, headers=HEADERS, timeout=20)
            resp.raise_for_status()
            soup = BeautifulSoup(resp.text, "lxml")

            file_url = None
            file_type = None
            for a in soup.select("a[href]"):
                href = a["href"]
                for ext in ("hwpx", "hwp", "pdf"):
                    if href.lower().endswith(f".{ext}"):
                        file_url = urljoin(url, href)
                        file_type = ext
                        break
                if file_url:
                    break

            content_div = soup.select_one(".board_view") or soup.select_one(".content")
            html_content = content_div.get_text(separator="\n", strip=True) if content_div else ""

            return {"file_url": file_url, "file_type": file_type, "html_content": html_content}
        except Exception:
            return {"file_url": None, "file_type": None, "html_content": ""}

    @staticmethod
    def _parse_date(date_text: str) -> str:
        date_text = re.sub(r"[^\d\-\.]", "", date_text)
        try:
            dt = datetime.strptime(date_text, "%Y.%m.%d")
            return dt.strftime("%Y-%m-%dT00:00:00+09:00")
        except ValueError:
            return datetime.now().strftime("%Y-%m-%dT00:00:00+09:00")


class KoreaPolicyBriefingCrawler:
    """정책브리핑 부동산 섹션 크롤러"""

    BASE_URL = "https://www.korea.kr"
    LIST_URL = "https://www.korea.kr/news/pressReleaseView.do?newsId=&srchType=R&policyType=&subId=&pageIndex=1&srchWord=부동산"
    CATEGORY = "부동산"
    SOURCE = "정책브리핑"

    def fetch(self) -> list[RawPolicy]:
        try:
            resp = requests.get(self.LIST_URL, headers=HEADERS, timeout=20)
            resp.raise_for_status()
            soup = BeautifulSoup(resp.text, "lxml")
            results = []
            for item in soup.select(".news_list li")[:10]:
                link_tag = item.select_one("a")
                if not link_tag:
                    continue
                title = item.select_one(".title")
                date_tag = item.select_one(".date")
                if not title or not date_tag:
                    continue
                url = urljoin(self.BASE_URL, link_tag.get("href", ""))
                results.append(RawPolicy(
                    url=url,
                    title=title.get_text(strip=True),
                    source=self.SOURCE,
                    published_at=self._parse_date(date_tag.get_text(strip=True)),
                    file_url=None,
                    file_type=None,
                    html_content="",
                ))
            return results
        except Exception:
            return []

    @staticmethod
    def _parse_date(date_text: str) -> str:
        try:
            dt = datetime.strptime(date_text.strip(), "%Y.%m.%d")
            return dt.strftime("%Y-%m-%dT00:00:00+09:00")
        except ValueError:
            return datetime.now().strftime("%Y-%m-%dT00:00:00+09:00")


# 크롤러 레지스트리 — 새 사이트 추가 시 여기에만 등록
CRAWLERS = [
    MolitCrawler(),
    KoreaPolicyBriefingCrawler(),
]
