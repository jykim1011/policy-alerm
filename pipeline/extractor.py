import io
import os
import re
import subprocess
import tempfile
import zipfile
import xml.etree.ElementTree as ET

import pymupdf
from bs4 import BeautifulSoup

# 한글 문서의 이미지 대체텍스트 노이즈. 요약 입력을 오염시키므로 라인 단위로 제거한다.
# (국토부 hwpx에서 관찰: "그림입니다.", "원본 그림의 이름: ….png" 등)
_NOISE_LINE = re.compile(
    r"^(그림입니다\.?"
    r"|원본 그림의 (이름|크기)\s*:.*"
    r"|사진 찍은 날짜\s*:.*"
    r"|프로그램 이름\s*:.*)$"
)

# 정책브리핑(korea.kr) 기사 하단의 저작권 고지·공유 위젯·댓글 운영원칙 상용구.
# 정책 내용이 아닌데도 요약 입력에 섞여, 모델이 "공공누리 제1유형:출처표시",
# "저작권법 제37조 및 제138조" 같은 항목을 용어 풀이에 실어 보내는 원인이었다.
# 부처·문서를 가리지 않고 반복되는 문구만 좁게 지정한다(본문 오탐 방지).
_BOILERPLATE_LINE = re.compile(
    r"^("
    r"이 자료는"
    r"|텍스트에 한하여 공공누리.*"
    r"|공공누리 제\d유형.*"
    r"|단, 텍스트를 제외한 사진.*"
    r"|.*정책브리핑이 저작권을 보유하고 있지 않으므로.*"
    r"|.*보도자료를 전재하여 제공함을 알려드립니다.*"
    r"|.*저작권법.{0,20}제138조.*"
    r"|저작권정책|담당자안내|이전다음기사 영역|이전기사|다음기사"
    r"|공유하기|공유|공유 닫기|즐겨찾기|URL 복사|페이스북|밴드|카카오톡"
    r"|열기|닫기|보기|목록|인쇄하기|본문듣기|시작|정지|상단으로 이동"
    r"|글자크기 설정( (열기|닫기))?"
    r"|작게|보통|크게|아주크게|최대크게"
    r"|첨부파일|바로보기|내려받기|부처별 뉴스 이동"
    r"|댓글|운영원칙( (열기|닫기))?"
    r"|정책브리핑 게시물 운영원칙에 따라.*"
    r"|\d{1,2}\. (타인의|확인되지 않은|공공질서|욕설|불법복제|영리를|범죄와|공인이나|해당 기사나|동일한|기타 관계법령|수사기관).*"
    r"|게시하는 경우"
    r")$"
)

# 기사 본문이 아닌 사이트 구성요소. footer 태그를 쓰지 않는 정책브리핑 구조상
# 클래스명으로 걷어내야 인기뉴스·댓글 운영원칙까지 요약 입력에 딸려오지 않는다.
_CHROME_CLASSES = ("article_footer", "view_opt", "livereContainer", "lv-container")


def _clean_noise(text: str) -> str:
    """이미지 대체텍스트와 기사 하단 상용구를 라인 단위로 걷어낸다."""
    kept = []
    for line in text.splitlines():
        stripped = line.strip()
        if _NOISE_LINE.match(stripped) or _BOILERPLATE_LINE.match(stripped):
            continue
        kept.append(line)
    return "\n".join(kept)


def extract_text(file_content: bytes, file_type: str) -> str:
    """파일 타입에 따라 텍스트를 추출한다. 실패 시 빈 문자열 반환."""
    try:
        if file_type == "hwpx":
            return _clean_noise(_extract_hwpx(file_content))
        elif file_type == "hwp":
            return _clean_noise(_extract_hwp(file_content))
        elif file_type == "pdf":
            return _clean_noise(_extract_pdf(file_content))
        elif file_type == "html":
            return _extract_html(file_content.decode("utf-8", errors="ignore"))
        else:
            return ""
    except Exception:
        return ""


def _extract_hwpx(content: bytes) -> str:
    """HWPX(ZIP 기반 XML)에서 텍스트 추출"""
    texts = []
    with zipfile.ZipFile(io.BytesIO(content)) as z:
        section_files = [n for n in z.namelist() if n.startswith("Contents/section")]
        for section_file in sorted(section_files):
            with z.open(section_file) as f:
                root = ET.parse(f).getroot()
                for elem in root.iter():
                    if elem.text and elem.text.strip():
                        texts.append(elem.text.strip())
    return "\n".join(texts)


def _extract_hwp(content: bytes) -> str:
    """HWP 바이너리에서 텍스트 추출 (hwp5txt CLI 사용)"""
    with tempfile.NamedTemporaryFile(suffix=".hwp", delete=False) as f:
        f.write(content)
        tmp_path = f.name
    try:
        # hwp5txt는 UTF-8로 출력한다. encoding을 지정하지 않으면 text=True가
        # 로케일 인코딩으로 디코딩해(윈도우 cp949) 한글에서 UnicodeDecodeError로
        # 죽는다. 실행 환경과 무관하게 동작하도록 인코딩을 못 박는다.
        result = subprocess.run(
            ["hwp5txt", tmp_path],
            capture_output=True, text=True, timeout=30,
            encoding="utf-8", errors="replace",
        )
        if result.returncode != 0:
            raise subprocess.SubprocessError(f"hwp5txt failed: {result.stderr}")
        return result.stdout
    finally:
        os.unlink(tmp_path)


def _extract_pdf(content: bytes) -> str:
    """PDF에서 텍스트 추출"""
    doc = pymupdf.open(stream=content, filetype="pdf")
    return "\n".join(page.get_text() for page in doc)


def _extract_html(html: str) -> str:
    """HTML 본문에서 텍스트 추출.

    정책브리핑은 저작권 고지·이전다음기사·공유 위젯·댓글 운영원칙을 <footer>가 아니라
    본문 아래 div로 두기 때문에, 태그 이름만으로 지우면 기사 분량의 40% 남짓이
    상용구로 채워진 채 요약 모델에 들어간다. 클래스명으로도 함께 걷어낸다.
    """
    soup = BeautifulSoup(html, "lxml")
    for tag in soup(["script", "style", "nav", "footer", "header"]):
        tag.decompose()
    for cls in _CHROME_CLASSES:
        for tag in soup.find_all(class_=cls):
            tag.decompose()
    return _clean_noise(soup.get_text(separator="\n", strip=True))
