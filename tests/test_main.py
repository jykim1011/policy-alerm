from pipeline.main import _classify_subcategory


# 부동산 세부 분류
def test_real_estate_subscription():
    assert _classify_subcategory("국민 청약 당첨 확대", "부동산") == "청약"

def test_real_estate_loan():
    assert _classify_subcategory("LTV 규제 완화 방안", "부동산") == "대출"

def test_real_estate_tax():
    assert _classify_subcategory("취득세 감면 시행", "부동산") == "세금"

def test_real_estate_redevelopment():
    assert _classify_subcategory("재건축 규제 완화", "부동산") == "재개발"

def test_real_estate_rent():
    assert _classify_subcategory("전세사기 피해자 지원", "부동산") == "전월세"

def test_real_estate_fallback():
    assert _classify_subcategory("부동산 시장 동향", "부동산") == "부동산"


# 신규 카테고리 — 서브카테고리 = 카테고리 자체
def test_employment_subcategory():
    assert _classify_subcategory("청년 취업 지원 확대", "고용") == "고용"

def test_welfare_subcategory():
    assert _classify_subcategory("기초생활 수급자 확대", "복지") == "복지"

def test_startup_subcategory():
    assert _classify_subcategory("소상공인 대출 지원", "창업") == "창업"

def test_childcare_subcategory():
    assert _classify_subcategory("육아휴직 급여 인상", "육아") == "육아"

def test_education_subcategory():
    assert _classify_subcategory("국가장학금 소득기준 완화", "교육") == "교육"
