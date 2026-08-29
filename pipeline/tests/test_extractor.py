import zipfile
import io
import xml.etree.ElementTree as ET
from pipeline.extractor import extract_text, _extract_hwpx, _extract_pdf, _extract_html, _clean_noise

def _make_hwpx_bytes() -> bytes:
    """최소 HWPX 구조를 가진 zip 파일 생성"""
    buf = io.BytesIO()
    ns = "http://www.hancom.co.kr/hwpml/2012/paragraph"
    xml_content = f"""<?xml version="1.0"?>
<BodyText xmlns:hp="{ns}">
  <hp:P><hp:Run><hp:T>청약 제도가 바뀝니다</hp:T></hp:Run></hp:P>
</BodyText>"""
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr("Contents/section0.xml", xml_content)
    return buf.getvalue()

def test_extract_hwpx():
    content = _make_hwpx_bytes()
    text = _extract_hwpx(content)
    assert "청약 제도가 바뀝니다" in text

def test_extract_html():
    html = "<html><body><div class='content'><p>정책 내용입니다</p></div></body></html>"
    text = _extract_html(html)
    assert "정책 내용입니다" in text

def test_extract_text_dispatches_by_type(tmp_path):
    hwpx_bytes = _make_hwpx_bytes()
    result = extract_text(hwpx_bytes, "hwpx")
    assert len(result) > 0

def test_extract_text_falls_back_on_unknown_type():
    result = extract_text(b"", "unknown")
    assert result == ""


def _make_hwpx_with_noise() -> bytes:
    """이미지 대체텍스트 노이즈가 섞인 HWPX 생성 (실제 국토부 hwpx에서 관찰된 패턴)"""
    buf = io.BytesIO()
    ns = "http://www.hancom.co.kr/hwpml/2012/paragraph"
    lines = [
        "그림입니다.",
        "원본 그림의 이름: 슬로건_보도자료_상단.png",
        "원본 그림의 크기: 가로 2475pixel, 세로 525pixel",
        "투기과열지구 및 조정대상지역 추가 지정",
        "사진 찍은 날짜: 2025년 12월 11일 오후 9:06",
        "프로그램 이름 : Adobe Photoshop 27.1 (Windows)",
        "7월 1일부터 지정효력 발생",
    ]
    runs = "".join(f"<hp:P><hp:Run><hp:T>{l}</hp:T></hp:Run></hp:P>" for l in lines)
    xml_content = f'<?xml version="1.0"?><BodyText xmlns:hp="{ns}">{runs}</BodyText>'
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr("Contents/section0.xml", xml_content)
    return buf.getvalue()


def test_extract_text_strips_image_alt_noise():
    # 이미지 대체텍스트("그림입니다", "원본 그림의 …" 등)는 요약 입력을 오염시키므로 제거한다.
    text = extract_text(_make_hwpx_with_noise(), "hwpx")
    assert "투기과열지구 및 조정대상지역 추가 지정" in text
    assert "7월 1일부터 지정효력 발생" in text
    assert "그림입니다" not in text
    assert "원본 그림의 이름" not in text
    assert "원본 그림의 크기" not in text
    assert "사진 찍은 날짜" not in text
    assert "프로그램 이름" not in text


# ── 기사 하단 상용구 제거 ──
# 정책브리핑 기사 끝의 저작권 고지·공유 위젯·댓글 운영원칙이 요약 입력에 섞여
# 용어 풀이에 "공공누리 제1유형:출처표시" 같은 항목이 실려 나가던 문제.

def test_clean_noise_strips_copyright_boilerplate():
    text = "\n".join([
        "청약 제도가 바뀝니다.",
        "이 자료는",
        "텍스트에 한하여 공공누리 제1유형(출처표시)의 조건",
        "공공누리 제1유형:출처표시",
        "단, 텍스트를 제외한 사진·이미지·일러스트·동영상 등 자료의 대부분은",
        "저작권정책",
        "담당자안내",
        "무주택 세대주가 대상입니다.",
    ])
    out = _clean_noise(text)
    assert "청약 제도가 바뀝니다." in out
    assert "무주택 세대주가 대상입니다." in out
    assert "공공누리" not in out
    assert "저작권정책" not in out


def test_clean_noise_strips_share_widget_and_comment_policy():
    text = "\n".join([
        "지원금은 8월 31일까지 사용해야 합니다.",
        "공유하기",
        "페이스북",
        "카카오톡",
        "URL 복사",
        "댓글",
        "운영원칙",
        "정책브리핑 게시물 운영원칙에 따라 다음과 같은 게시물은 삭제 또는 계정이 차단 될 수 있습니다.",
        "1. 타인의 메일주소, 전화번호, 주민등록번호 등의 개인정보 또는 해당 정보를 게재하는 경우",
        "13. 수사기관 등의 공식적인 요청이 있는 경우",
    ])
    out = _clean_noise(text)
    assert out.strip() == "지원금은 8월 31일까지 사용해야 합니다."


def test_clean_noise_keeps_policy_body_containing_similar_words():
    """본문에 '공유', '댓글' 등이 문장 일부로 나오면 지우면 안 된다(라인 전체 일치일 때만 제거)."""
    text = "\n".join([
        "정부는 공유주택 공급을 확대한다.",
        "댓글 기능을 통해 의견을 받는다.",
    ])
    out = _clean_noise(text)
    assert out == text


def test_extract_html_removes_article_footer_block():
    html = """<html><body>
      <div class='article_wrap'><p>정책 본문입니다</p></div>
      <div class='article_footer'><p>공공누리 제1유형:출처표시</p><p>저작권정책</p></div>
    </body></html>"""
    text = _extract_html(html)
    assert "정책 본문입니다" in text
    assert "공공누리" not in text
    assert "저작권정책" not in text
