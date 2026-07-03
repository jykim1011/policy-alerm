from pipeline.crawler import _classify_category


def test_real_estate_title():
    assert _classify_category("주택공급 확대 방안") == "부동산"


def test_employment_title():
    assert _classify_category("청년 고용 지원 대책") == "고용"


def test_welfare_title():
    assert _classify_category("기초생활 급여 인상") == "복지"


def test_startup_title():
    assert _classify_category("소상공인 지원금 확대") == "창업"


def test_childcare_title():
    assert _classify_category("출산 장려금 신설") == "육아"


def test_education_title():
    assert _classify_category("국가장학금 대상 확대") == "교육"


def test_no_match():
    assert _classify_category("철도 인프라 투자 계획") is None


def test_exclude_term_jeonsegye():
    # "전세계" 는 "전세" 키워드의 오탐을 방지한다.
    assert _classify_category("전세계 주목하는 K-콘텐츠") is None


def test_plant_bunyang_not_real_estate():
    # "식물 분양" — "분양" 단독 키워드 오탐 방지.
    assert _classify_category("반려식물 분양 행사 개최") is None


def test_chartered_bus_not_real_estate():
    # "전세 버스"/"전세버스" — "전세" 단독 키워드 오탐 방지.
    assert _classify_category("명절 전세 버스 운행 확대") is None
    assert _classify_category("전세버스 안전점검 실시") is None


def test_jeonse_compounds_still_real_estate():
    # 부동산 문맥의 전세/분양 복합어는 여전히 매칭돼야 한다.
    assert _classify_category("전세사기 피해자 지원 확대") == "부동산"
    assert _classify_category("수도권 전세시장 안정 대책") == "부동산"
    assert _classify_category("분양가 상한제 개편") == "부동산"
    assert _classify_category("공공분양 물량 확대") == "부동산"
    assert _classify_category("전세 계약 전 '위험신호' 알려준다, 안심전세앱 9월 개편") == "부동산"


def test_startup_beats_welfare_for_jiwongeum():
    # "소상공인 지원금" — 창업이 복지보다 먼저 매칭돼야 한다.
    assert _classify_category("소상공인 지원금 신청 안내") == "창업"
