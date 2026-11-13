import json
import re
from datetime import date
from unittest.mock import MagicMock

from pipeline import market as mkt
from pipeline import market_stats as ms
from pipeline.backfill_market import tag_policy
from pipeline.models import PolicyItem, PolicySummary
from pipeline.publisher import publish_policy, update_index
from pipeline.summarizer import summarize_policy


# ── sector_map ──────────────────────────────────────────────────────────

def test_sector_map_codes_are_krx_short_codes_and_unique():
    seen = {}
    for key, s in mkt.load_sectors().items():
        assert s["description"]
        assert s["etfs"] or s["stocks"], key
        for item in s["etfs"] + s["stocks"]:
            assert re.fullmatch(r"\d{6}", item["code"]), item
            assert item["name"].strip()
            # 같은 코드가 다른 이름으로 두 번 나오면 둘 중 하나는 틀린 것이다.
            assert seen.setdefault(item["code"], item["name"]) == item["name"], item


# ── normalize_market ───────────────────────────────────────────────────

def test_normalize_market_drops_unknown_duplicate_and_advice():
    raw = {
        "sectors": [
            {"sector": "건설", "direction": "긍정", "reason": "주택 공급 예산 5조원 편성"},
            {"sector": "건설", "direction": "부정", "reason": "중복"},
            {"sector": "없는업종", "direction": "긍정", "reason": "근거"},
            {"sector": "은행", "direction": "긍정", "reason": "은행주가 오를 것으로 보여 매수 추천"},
            {"sector": "증권", "direction": "이상한값", "reason": "공매도 제도 개편"},
        ],
        "policy_type": "공급 확대",
        "investor_note": "대규모 공급 대책이에요.",
    }
    m = mkt.normalize_market(raw)
    assert [s["sector"] for s in m["sectors"]] == ["건설", "증권"]
    assert m["sectors"][1]["direction"] == "혼재"
    assert m["policy_type"] == "공급 확대"


def test_normalize_market_caps_sectors_and_empty_when_none():
    keys = mkt.sector_keys()[:5]
    raw = {"sectors": [{"sector": k, "direction": "긍정", "reason": "근거"} for k in keys],
           "policy_type": "x", "investor_note": "목표주가 상향"}
    m = mkt.normalize_market(raw)
    assert len(m["sectors"]) == mkt.MAX_SECTORS
    assert m["policy_type"] == "기타"
    assert m["investor_note"] == ""
    assert mkt.normalize_market({"sectors": [], "policy_type": "기타", "investor_note": ""}) == {}
    assert mkt.normalize_market(None) == {}


def test_build_market_block_attaches_curated_tickers():
    block = mkt.build_market_block({
        "sectors": [{"sector": "반도체", "direction": "긍정", "reason": "보조금"}],
        "policy_type": "예산·지원", "investor_note": "",
    })
    s = block["sectors"][0]
    assert {"code": "005930", "name": "삼성전자"} in s["stocks"]
    assert s["etfs"]
    assert "investor_note" not in block
    assert mkt.build_market_block({}) is None


# ── summarizer 연동 ─────────────────────────────────────────────────────

_BASE = {
    "what_changed": "반도체 보조금 3조원", "who_is_affected": "반도체 기업", "when_effective": "2027년 1월",
    "key_points": ["a"],
}


def test_summarize_policy_parses_market_and_schema_has_enum():
    model = MagicMock()
    model.complete.return_value = json.dumps({**_BASE, "market": {
        "sectors": [{"sector": "반도체", "direction": "긍정", "reason": "보조금 3조원 지원"}],
        "policy_type": "예산·지원", "investor_note": "대규모 재정 지원이에요.",
    }}, ensure_ascii=False)
    s = summarize_policy("반도체 지원", "본문", model=model)
    assert s.market["sectors"][0]["sector"] == "반도체"
    schema = model.complete.call_args.kwargs["schema"]
    enum = schema["properties"]["market"]["properties"]["sectors"]["items"]["properties"]["sector"]["enum"]
    assert "반도체" in enum
    # 프롬프트에 업종 목록이 들어간다
    assert "- 반도체:" in model.complete.call_args.args[0]


def test_summarize_policy_without_market_is_empty():
    model = MagicMock()
    model.complete.return_value = json.dumps(_BASE, ensure_ascii=False)
    assert summarize_policy("t", "x", model=model).market == {}


# ── publisher ───────────────────────────────────────────────────────────

def _item(market):
    return PolicyItem(
        id="p-2026-11-01-aaaa", category="금융", subcategory="금융", title="t", source="금융위원회",
        source_url="u", file_url=None, file_type=None, published_at="2026-11-01T09:00:00+09:00",
        summary=PolicySummary(what_changed="w", who_is_affected="x", when_effective="", key_points=[],
                              market=market),
    )


def test_publish_writes_market_block_and_index_sectors(tmp_path):
    m = {"sectors": [{"sector": "은행", "direction": "부정", "reason": "대출 규제"}],
         "policy_type": "규제 강화", "investor_note": "n", "impact": "보통"}
    publish_policy(_item(m), docs_root=str(tmp_path))
    update_index(_item(m), docs_root=str(tmp_path))
    d = json.loads((tmp_path / "policies" / "p-2026-11-01-aaaa.json").read_text(encoding="utf-8"))
    assert d["market"]["sectors"][0]["stocks"]
    assert d["market"]["impact"] == "보통"
    assert d["market_version"] == mkt.MARKET_VERSION
    idx = json.loads((tmp_path / "policies" / "index.json").read_text(encoding="utf-8"))
    assert idx["items"][0]["sectors"] == ["은행"]
    arch = json.loads((tmp_path / "archive" / "2026.json").read_text(encoding="utf-8"))
    assert arch["items"][0]["sectors"] == ["은행"]


def test_publish_without_market_omits_keys(tmp_path):
    publish_policy(_item({}), docs_root=str(tmp_path))
    update_index(_item({}), docs_root=str(tmp_path))
    d = json.loads((tmp_path / "policies" / "p-2026-11-01-aaaa.json").read_text(encoding="utf-8"))
    assert "market" not in d
    idx = json.loads((tmp_path / "policies" / "index.json").read_text(encoding="utf-8"))
    assert "sectors" not in idx["items"][0]


# ── backfill ───────────────────────────────────────────────────────────

def test_tag_policy_marks_version_even_without_sectors():
    model = MagicMock()
    model.complete.return_value = json.dumps(
        {"market": {"sectors": [], "policy_type": "기타", "investor_note": ""}})
    d = tag_policy({"id": "a", "title": "육아휴직 확대", "summary": {"what_changed": "w"},
                    "market_checked": True}, model)
    assert d["market_version"] == mkt.MARKET_VERSION and "market" not in d
    assert "market_checked" not in d


def test_publish_hides_low_impact_from_index(tmp_path):
    m = {"sectors": [{"sector": "건설", "direction": "긍정", "reason": "r"}],
         "policy_type": "공급 확대", "investor_note": "", "impact": "낮음"}
    publish_policy(_item(m), docs_root=str(tmp_path))
    update_index(_item(m), docs_root=str(tmp_path))
    idx = json.loads((tmp_path / "policies" / "index.json").read_text(encoding="utf-8"))
    assert "sectors" not in idx["items"][0]
    d = json.loads((tmp_path / "policies" / "p-2026-11-01-aaaa.json").read_text(encoding="utf-8"))
    assert d["market"]["impact"] == "낮음"  # 데이터는 남기고 화면에서만 숨긴다


# ── 중요도 ─────────────────────────────────────────────────────────────

F = lambda *vals: [{"label": "예산", "value": v} for v in vals]


def test_impact_rules():
    assert mkt.impact_of("신규", "발표", F("3.2조원")) == ("높음", 5)
    assert mkt.impact_of("신규", "발표", F("500억원")) == ("보통", 4)
    assert mkt.impact_of("신규", "의결·통과", F("500억원")) == ("보통", 5)  # 규모 작으면 높음 아님
    assert mkt.impact_of("신규", "의결·통과", F("2조원")) == ("높음", 6)
    assert mkt.impact_of("확대", "발표", F("5만 호")) == ("보통", 4)
    assert mkt.impact_of("확대", "검토·계획", []) == ("낮음", 1)
    assert mkt.impact_of("연장", "시행", F("10%")) == ("보통", 3)
    # 재확인은 규모와 무관하게 낮음
    assert mkt.impact_of("재확인", "시행", F("10조원"))[0] == "낮음"


def test_is_large_parses_korean_amounts():
    assert mkt._is_large(F("1.5조원"))
    assert mkt._is_large(F("12,000억원"))
    assert mkt._is_large(F("3만 세대"))
    assert not mkt._is_large(F("21만 가구"))  # 수혜 가구 수는 규모가 아니다
    assert not mkt._is_large(F("9,000억원"))
    assert not mkt._is_large(F("5천 명"))


def test_normalize_market_new_fields_and_validation():
    raw = {"sectors": [{"sector": "건설", "strength": "직접", "direction": "긍정", "reason": "공급"}],
           "policy_type": "공급 확대", "novelty": "신규", "stage": "발표",
           "figures": [{"label": "공급", "value": "5만 호"}, {"label": "규모", "value": "대폭"},
                       {"label": "예산", "value": "2조원"}, {"label": "a", "value": "1"},
                       {"label": "b", "value": "2"}],
           "effective_date": "2027년 1월", "investor_note": ""}
    m = mkt.normalize_market(raw)
    assert [f["value"] for f in m["figures"]] == ["5만 호", "2조원", "1"]  # 수치 없는 값 제외, 최대 3개
    assert m["effective_date"] == ""  # 형식 불일치
    assert m["impact"] == "높음"
    raw["effective_date"] = "2027-01"
    raw["novelty"] = "이상한값"
    m = mkt.normalize_market(raw)
    assert m["effective_date"] == "2027-01"
    assert m["novelty"] == "재확인" and m["impact"] == "낮음"


def test_sector_names_impact_filter():
    m = {"sectors": [{"sector": "건설"}], "impact": "보통"}
    assert mkt.sector_names(m) == ["건설"]
    assert mkt.sector_names(m, mkt.SHOW_IMPACT) == ["건설"]
    assert mkt.sector_names(m, mkt.NOTIFY_IMPACT) == ["건설"]
    assert mkt.sector_names({**m, "impact": "낮음"}, mkt.NOTIFY_IMPACT) == []
    assert mkt.sector_names({"sectors": [{"sector": "건설"}]}, mkt.SHOW_IMPACT) == []


# ── 반응 통계 ───────────────────────────────────────────────────────────

def test_event_returns_uses_close_before_publication():
    closes = {"20261030": 100.0, "20261102": 110.0, "20261103": 99.0,
              "20261104": 100, "20261105": 100, "20261106": 105.0}
    r = ms.event_returns(closes, date(2026, 11, 2))
    assert r["d1"] == 0.1
    assert r["d5"] == 0.05
    assert "d20" not in r
    assert ms.event_returns({"20261102": 1.0}, date(2026, 11, 2)) == {}


def test_build_sector_stats_hides_numbers_below_min_samples(tmp_path, monkeypatch):
    monkeypatch.setattr(ms, "MARKET_DIR", tmp_path)
    items = {}
    for i, v in enumerate([0.02, -0.01, 0.03]):
        items[f"p{i}|건설"] = {"policy_id": f"p{i}", "title": "t", "date": f"2026-10-0{i+1}",
                             "sector": "건설", "direction": "긍정", "d5": v, "x5": v - 0.01}
    items["q|은행"] = {"policy_id": "q", "title": "t", "date": "2026-10-01",
                      "sector": "은행", "direction": "부정", "d5": 0.01}
    stats = ms.build_sector_stats({"items": items})
    c = stats["sectors"]["건설"]
    assert c["count"] == 3 and c["avg_d5"] == round(0.04 / 3, 4)
    assert c["up_ratio_d5"] == 0.67
    assert c["by_direction"]["긍정"]["count"] == 3
    assert c["recent"][0]["policy_id"] == "p2"
    assert "avg_d5" not in stats["sectors"]["은행"]
    assert (tmp_path / "sector_stats.json").exists()


def test_price_client_raises_when_key_not_registered():
    session = MagicMock()
    session.get.return_value.json.side_effect = ValueError
    session.get.return_value.text = "<OpenAPI_ServiceResponse>SERVICE_KEY_IS_NOT_REGISTERED_ERROR"
    client = ms.PriceClient("k", set(), session=session)
    try:
        client.closes("005930", date(2026, 10, 1), date(2026, 10, 30))
        assert False, "should raise"
    except ms.PriceApiUnavailable:
        pass


def test_price_client_filters_partial_code_matches():
    session = MagicMock()
    session.get.return_value.json.return_value = {"response": {
        "header": {"resultCode": "00"},
        "body": {"totalCount": 2, "items": {"item": [
            {"basDt": "20261030", "srtnCd": "005930", "clpr": "60,000"},
            {"basDt": "20261030", "srtnCd": "005935", "clpr": "50000"},
        ]}},
    }}
    out = ms.PriceClient("k", set(), session=session).closes("005930", date(2026, 10, 1), date(2026, 10, 30))
    assert out == {"20261030": 60000.0}


def test_normalize_market_drops_indirect_and_self_admitted_weak():
    raw = {"sectors": [
        {"sector": "건설", "strength": "간접", "direction": "긍정", "reason": "회의에서 언급"},
        {"sector": "은행", "strength": "직접", "direction": "긍정", "reason": "직접 예산이나 규제 내용은 확인되지 않아요"},
        {"sector": "증권", "strength": "직접", "direction": "긍정", "reason": "결제주기 T+1 단축"},
    ], "policy_type": "규제 완화", "investor_note": ""}
    assert [s["sector"] for s in mkt.normalize_market(raw)["sectors"]] == ["증권"]


def test_figures_drop_penalties_and_beneficiary_counts():
    raw = [{"label": "과태료", "value": "1천만 원"}, {"label": "대상", "value": "21만 가구"},
           {"label": "예산", "value": "3조원"}]
    assert [f["label"] for f in mkt._figures(raw)] == ["예산"]


# ── 캘린더 ─────────────────────────────────────────────────────────────

from pipeline.market_calendar import build_calendar, write_calendar


def _pol(pid, pub, eff, impact="보통", sectors=("건설",), direction="긍정"):
    return {"id": pid, "title": pid, "published_at": f"{pub}T09:00:00+09:00",
            "summary": {"easy_title": f"쉬운 {pid}"},
            "market": {"sectors": [{"sector": s, "direction": direction} for s in sectors],
                       "impact": impact, "novelty": "신규", "stage": "발표",
                       "figures": [{"label": "예산", "value": "1조원"}], "effective_date": eff}}


def test_build_calendar_upcoming_filters_and_sorts():
    today = date(2026, 11, 13)
    cal = build_calendar([
        _pol("past", "2026-11-01", "2026-11-12"),
        _pol("month_now", "2026-11-01", "2026-11"),       # 월만 → 이번 달이면 포함
        _pol("last_month", "2026-10-01", "2026-10"),
        _pol("dec_high", "2026-11-02", "2026-12-01", impact="높음"),
        _pol("dec", "2026-11-03", "2026-12-01"),
        _pol("too_far", "2026-11-03", "2027-12-01"),
        _pol("nodate", "2026-11-03", ""),
    ], today)
    assert [u["policy_id"] for u in cal["upcoming"]] == ["month_now", "dec_high", "dec"]
    assert cal["upcoming"][0]["date_exact"] is False
    assert cal["upcoming"][1]["title"] == "쉬운 dec_high"


def test_build_calendar_momentum_counts_recent_by_sector():
    today = date(2026, 11, 13)
    cal = build_calendar([
        _pol("a", "2026-11-10", "", sectors=("건설", "은행")),
        _pol("b", "2026-11-01", "", direction="부정"),
        _pol("c", "2026-11-05", "", impact="높음"),
        _pol("old", "2026-09-01", ""),
    ], today)
    top = cal["momentum"][0]
    assert top["sector"] == "건설" and top["count"] == 3
    assert (top["positive"], top["negative"], top["high"]) == (2, 1, 1)
    assert top["policies"][0]["policy_id"] == "c"  # 높음 먼저
    # 건수와 목록이 일치해야 정책별 긍정·부정을 확인할 수 있다
    assert len(top["policies"]) == top["count"]
    assert {p["policy_id"]: p["direction"] for p in top["policies"]}["b"] == "부정"
    assert {m["sector"] for m in cal["momentum"]} == {"건설", "은행"}


def test_write_calendar_ignores_low_impact(tmp_path):
    (tmp_path / "policies").mkdir()
    for p in [_pol("show", "2026-11-10", "2026-12-01"), _pol("low", "2026-11-10", "2026-12-01", impact="낮음")]:
        (tmp_path / "policies" / f"{p['id']}.json").write_text(json.dumps(p, ensure_ascii=False), encoding="utf-8")
    cal = write_calendar(tmp_path, today=date(2026, 11, 13))
    assert [u["policy_id"] for u in cal["upcoming"]] == ["show"]
    assert (tmp_path / "market" / "calendar.json").exists()
