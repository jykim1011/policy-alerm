from pipeline.models import PolicyItem, PolicySummary, RawPolicy

def test_policy_item_has_required_fields():
    item = PolicyItem(
        id="molit-2026-05-29-001",
        category="부동산",
        subcategory="청약",
        title="청약 제도 개편",
        source="국토교통부",
        source_url="https://example.com",
        file_url=None,
        file_type=None,
        published_at="2026-05-29T09:00:00+09:00",
    )
    assert item.id == "molit-2026-05-29-001"
    assert item.file_url is None

def test_raw_policy_url_hash():
    raw = RawPolicy(url="https://example.com/policy", title="제목", source="국토교통부",
                    published_at="2026-05-29", file_url=None, file_type=None, html_content="내용")
    assert len(raw.url_hash) == 64  # sha256 hex
