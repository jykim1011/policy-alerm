import html
import json
import os
from datetime import datetime, timezone, timedelta

import requests
from bs4 import BeautifulSoup

from pipeline.models import RawPolicy

SEEN_FILE = "pipeline/seen_policies.json"
HEADERS = {"User-Agent": "Mozilla/5.0 (compatible; PolicyBot/1.0)"}
KST = timezone(timedelta(hours=9))

# 정책브리핑 보도자료 OpenAPI (공공데이터포털, 문화체육관광부 1371000).
# 정부 사이트 직접 크롤링은 해외/데이터센터 IP를 차단(Connection reset)하므로
# 차단되지 않는 apis.data.go.kr 게이트웨이를 사용한다.
PRESS_RELEASE_API = "http://apis.data.go.kr/1371000/pressReleaseService/pressReleaseList"

# 카테고리별 매칭 키워드. 순서가 우선순위 — 창업은 복지보다 앞에 있어야
# "소상공인 지원금" 같은 중의적 제목이 창업으로 분류된다.
CATEGORY_KEYWORDS: dict[str, dict] = {
    "부동산": {
        "keywords": (
            "부동산", "주택", "청약", "분양", "전세", "월세", "임대주택", "임대차",
            "재개발", "재건축", "정비사업", "LTV", "DSR", "종부세", "양도세", "취득세",
            "주담대", "분양가", "공시가격", "그린벨트", "신도시", "택지", "주거",
            "아파트", "주택담보", "다주택", "1주택", "보금자리", "공공주택",
        ),
        "exclude": ("전세계", "인재개발", "씨닭", "닭 분양"),
    },
    "고용": {
        "keywords": (
            "고용", "취업", "일자리", "실업급여", "채용", "고용보험",
            "고용지원", "청년취업", "취업지원", "구직",
        ),
        "exclude": (),
    },
    "창업": {
        "keywords": ("창업", "소상공인", "자영업", "중소기업", "폐업지원", "소기업"),
        "exclude": (),
    },
    "육아": {
        "keywords": (
            "출산", "육아", "보육", "아이돌봄", "어린이집", "임신", "산후",
            "육아휴직", "유아",
        ),
        "exclude": (),
    },
    "교육": {
        "keywords": ("장학금", "학자금", "교육비", "등록금", "학비", "교육지원"),
        "exclude": (),
    },
    "복지": {
        "keywords": (
            "복지", "지원금", "기초생활", "수당", "바우처", "의료급여",
            "장애인 지원", "사회보장", "생계급여",
        ),
        "exclude": (),
    },
}


def load_seen(path: str = SEEN_FILE) -> set[str]:
    with open(path, encoding="utf-8") as f:
        return set(json.load(f)["seen"])


def save_seen(seen: set[str], path: str = SEEN_FILE) -> None:
    with open(path, "w", encoding="utf-8") as f:
        json.dump({"seen": sorted(seen)}, f, ensure_ascii=False)


def is_new_policy(url_hash: str, seen: set[str]) -> bool:
    return url_hash not in seen


def _text(node, tag: str) -> str:
    el = node.find(tag)
    # CDATA 안에 &quot; &middot; 등 HTML 엔티티가 리터럴로 들어오므로 디코딩한다.
    return html.unescape(el.get_text(strip=True)) if el else ""


def _strip_html(raw_html: str) -> str:
    if not raw_html:
        return ""
    text = BeautifulSoup(raw_html, "lxml").get_text(separator=" ", strip=True)
    return html.unescape(text)


class PolicyBriefingApiCrawler:
    """정책브리핑 보도자료 OpenAPI 크롤러 — 부동산, 고용, 창업, 육아, 교육, 복지 6개 카테고리 수집."""

    SOURCE = "정책브리핑"

    # API는 한 번에 최대 3일(시작·종료일 차이 2일)까지만 조회 가능하므로
    # lookback_days를 3일 윈도우로 나눠 순회하며 백로그를 채운다.
    def __init__(self, lookback_days: int = 14, num_rows: int = 200, max_pages: int = 5):
        self.lookback_days = lookback_days
        self.num_rows = num_rows
        self.max_pages = max_pages

    def fetch(self) -> list[RawPolicy]:
        key = (os.environ.get("DATA_GO_KR_KEY") or "").strip()
        if not key:
            raise RuntimeError("DATA_GO_KR_KEY 환경변수가 설정되지 않았습니다")

        now = datetime.now(KST)
        results: list[RawPolicy] = []
        seen_urls: set[str] = set()

        days_done = 0
        while days_done < self.lookback_days:
            end = now - timedelta(days=days_done)
            start = end - timedelta(days=2)  # 3일(포함) 윈도우
            for page in range(1, self.max_pages + 1):
                resp = requests.get(
                    PRESS_RELEASE_API,
                    params={
                        "serviceKey": key,
                        "startDate": start.strftime("%Y%m%d"),
                        "endDate": end.strftime("%Y%m%d"),
                        "pageNo": str(page),
                        "numOfRows": str(self.num_rows),
                    },
                    headers=HEADERS,
                    timeout=30,
                )
                resp.raise_for_status()
                soup = BeautifulSoup(resp.text, "xml")
                nodes = soup.find_all("NewsItem")
                if not nodes:
                    break
                parsed = self._parse_nodes(nodes)
                print(f"  [{start.strftime('%m/%d')}~{end.strftime('%m/%d')}] p{page}: API {len(nodes)}건 → 키워드 매칭 {len(parsed)}건")
                for p in parsed:
                    if p.url in seen_urls:
                        continue
                    seen_urls.add(p.url)
                    results.append(p)
                if len(nodes) < self.num_rows:
                    break  # 마지막 페이지
            days_done += 3

        return results

    @classmethod
    def _parse(cls, xml_text: str) -> list[RawPolicy]:
        soup = BeautifulSoup(xml_text, "xml")
        return cls._parse_nodes(soup.find_all("NewsItem"))

    @classmethod
    def _parse_nodes(cls, nodes) -> list[RawPolicy]:
        out: list[RawPolicy] = []

        for node in nodes:
            title = _text(node, "Title")
            ministry = _text(node, "MinisterCode")
            category = cls._classify_category(title)
            if category is None:
                continue

            content = _strip_html(node.find("DataContents").get_text() if node.find("DataContents") else "")

            news_id = _text(node, "NewsItemId")
            original_url = _text(node, "OriginalUrl")
            file_name = _text(node, "FileName")
            file_url = _text(node, "FileUrl")

            url = original_url or (
                f"https://www.korea.kr/briefing/pressReleaseView.do?newsId={news_id}"
            )
            out.append(
                RawPolicy(
                    url=url,
                    title=title,
                    source=ministry or cls.SOURCE,
                    published_at=cls._parse_date(_text(node, "ApproveDate")),
                    file_url=file_url or None,
                    file_type=cls._file_type(file_name),
                    html_content=content,
                    category=category,
                )
            )

        return out

    @staticmethod
    def _classify_category(title: str) -> str | None:
        for category, cfg in CATEGORY_KEYWORDS.items():
            if any(bad in title for bad in cfg["exclude"]):
                continue
            if any(kw in title for kw in cfg["keywords"]):
                return category
        return None

    @staticmethod
    def _file_type(file_name: str) -> str | None:
        name = (file_name or "").lower()
        for ext in ("hwpx", "hwp", "pdf"):
            if name.endswith(f".{ext}"):
                return ext
        return None

    @staticmethod
    def _parse_date(approve_date: str) -> str:
        # 예: "05/30/2026 01:00:00"
        try:
            dt = datetime.strptime(approve_date, "%m/%d/%Y %H:%M:%S")
            return dt.strftime("%Y-%m-%dT%H:%M:%S+09:00")
        except (ValueError, TypeError):
            return datetime.now(KST).strftime("%Y-%m-%dT%H:%M:%S+09:00")


# 크롤러 레지스트리 — 새 소스 추가 시 여기에만 등록.
CRAWLERS = [
    PolicyBriefingApiCrawler(),
]
