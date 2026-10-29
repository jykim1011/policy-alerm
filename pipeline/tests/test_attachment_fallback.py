from unittest.mock import MagicMock, patch

from pipeline import main
from pipeline.crawler import find_page_attachment

PAGE = """<html><body><div class="filedown"><dl><dd>
<p><span><a href="/common/download.do?fileId=1">설명자료.pdf</a></span>
<span><a class="view" href="/common/docViewer.do?fileId=1">바로보기</a></span></p>
<p><span><a href="/common/download.do?fileId=2">설명자료.hwpx</a></span></p>
</dd></dl></div></body></html>"""


def _page(html):
    resp = MagicMock()
    resp.content = html.encode("utf-8")
    return resp


def test_find_page_attachment_prefers_hwpx():
    with patch("pipeline.crawler.requests.get", return_value=_page(PAGE)):
        url, ext = find_page_attachment("https://www.korea.kr/briefing/actuallyView.do?newsId=1")
    assert (url, ext) == ("https://www.korea.kr/common/download.do?fileId=2", "hwpx")


def test_find_page_attachment_none_when_no_files():
    with patch("pipeline.crawler.requests.get", return_value=_page("<html></html>")):
        assert find_page_attachment("https://www.korea.kr/x") is None


def test_stub_body_without_api_file_uses_page_attachment():
    """OpenAPI가 FileUrl을 비워 보내도 상세 페이지의 첨부로 원문을 채운다(제목만 요약 방지)."""
    with patch.object(main, "find_page_attachment", return_value=("u", "hwpx")), \
         patch.object(main, "download_file", return_value=b"x"), \
         patch.object(main, "extract_text", return_value="첨부 원문 " * 100):
        text = main._with_attachment_text("", None, None, "https://www.korea.kr/x")
    assert text.startswith("첨부 원문")


def test_title_key_matches_joint_press_releases():
    assert main._title_key("부동산정책, 국민의 목소리를 듣습니다.") == \
        main._title_key("부동산정책, 국민의 목소리를 듣습니다")
