import pytest
from pipeline.crawler import (
    PolicyBriefingApiCrawler,
    load_seen,
    save_seen,
    is_new_policy,
)

# 실제 data.go.kr 1371000 pressReleaseService 응답 구조를 본뜬 샘플.
SAMPLE_XML = """<?xml version="1.0" encoding="UTF-8"?>
<response>
  <header><resultCode>0</resultCode><resultMsg>NORMAL_SERVICE</resultMsg></header>
  <body>
    <NewsItem>
      <NewsItemId>156764520</NewsItemId>
      <ApproveDate>05/30/2026 01:00:00</ApproveDate>
      <MinisterCode>국토교통부</MinisterCode>
      <Title><![CDATA[수도권 전세시장 안정 대책 발표]]></Title>
      <DataContents><![CDATA[<p style="x">전세 임대 공급을 확대한다.</p>]]></DataContents>
      <OriginalUrl>https://www.korea.kr/briefing/pressReleaseView.do?newsId=156764520</OriginalUrl>
      <FileUrl>https://www.korea.kr/common/download.do?tblKey=GMN&amp;fileId=1</FileUrl>
      <FileName>전세대책.hwpx</FileName>
    </NewsItem>
    <NewsItem>
      <NewsItemId>156764519</NewsItemId>
      <ApproveDate>05/30/2026 00:00:00</ApproveDate>
      <MinisterCode>과학기술정보통신부</MinisterCode>
      <Title><![CDATA[주요 7개국 디지털 기술 장관회의 참석]]></Title>
      <DataContents><![CDATA[<p>인공지능 안전을 논의한다.</p>]]></DataContents>
      <OriginalUrl>https://www.korea.kr/briefing/pressReleaseView.do?newsId=156764519</OriginalUrl>
      <FileUrl></FileUrl>
      <FileName></FileName>
    </NewsItem>
  </body>
</response>"""


def test_parse_keeps_only_real_estate_items():
    items = PolicyBriefingApiCrawler._parse(SAMPLE_XML)
    assert len(items) == 1
    assert "전세" in items[0].title


def test_parse_extracts_expected_fields():
    p = PolicyBriefingApiCrawler._parse(SAMPLE_XML)[0]
    assert p.url == "https://www.korea.kr/briefing/pressReleaseView.do?newsId=156764520"
    assert p.source == "국토교통부"
    assert p.published_at == "2026-05-30T01:00:00+09:00"
    assert p.file_type == "hwpx"
    assert "전세 임대 공급을 확대한다" in p.html_content
    assert "<p" not in p.html_content  # HTML 태그는 제거됨


def test_parse_decodes_html_entities_in_title():
    xml = """<response><body><NewsItem>
      <NewsItemId>1</NewsItemId>
      <ApproveDate>05/30/2026 00:00:00</ApproveDate>
      <MinisterCode>국토교통부</MinisterCode>
      <Title><![CDATA[&quot;전세사기&quot; 예방&middot;대응 강화]]></Title>
      <DataContents><![CDATA[<p>내용</p>]]></DataContents>
      <OriginalUrl>https://example.com/1</OriginalUrl>
    </NewsItem></body></response>"""
    p = PolicyBriefingApiCrawler._parse(xml)[0]
    assert p.title == '"전세사기" 예방·대응 강화'


def test_parse_empty_body_returns_empty_list():
    xml = '<response><header><resultCode>0</resultCode></header><body></body></response>'
    assert PolicyBriefingApiCrawler._parse(xml) == []


def test_fetch_raises_without_api_key(monkeypatch):
    monkeypatch.delenv("DATA_GO_KR_KEY", raising=False)
    with pytest.raises(RuntimeError):
        PolicyBriefingApiCrawler().fetch()


def test_load_seen_returns_set(tmp_path):
    seen_file = tmp_path / "seen.json"
    seen_file.write_text('{"seen": ["abc123", "def456"]}')
    assert load_seen(str(seen_file)) == {"abc123", "def456"}


def test_save_and_load_roundtrip(tmp_path):
    seen_file = tmp_path / "seen.json"
    seen_file.write_text('{"seen": []}')
    save_seen({"hash1", "hash2"}, str(seen_file))
    assert load_seen(str(seen_file)) == {"hash1", "hash2"}


def test_is_new_policy():
    seen = {"existing_hash"}
    assert is_new_policy("existing_hash", seen) is False
    assert is_new_policy("new_hash", seen) is True
