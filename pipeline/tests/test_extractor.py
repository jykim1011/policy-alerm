import zipfile
import io
import xml.etree.ElementTree as ET
from pipeline.extractor import extract_text, _extract_hwpx, _extract_pdf, _extract_html

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
