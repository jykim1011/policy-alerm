import hashlib
import json
from datetime import datetime, timezone, timedelta
from pathlib import Path

from pipeline.models import PolicyItem, PolicySummary

DOCS_ROOT = "docs"
KST = timezone(timedelta(hours=9))


def _summary_dict(s: PolicySummary) -> dict:
    """요약을 JSON 직렬화. 보강 필드는 값이 있을 때만 넣어 기존 스키마와 호환되게 한다."""
    d = {
        "what_changed": s.what_changed,
        "who_is_affected": s.who_is_affected,
        "when_effective": s.when_effective,
        "key_points": s.key_points,
    }
    if getattr(s, "background", ""):
        d["background"] = s.background
    if getattr(s, "eligibility", None):
        d["eligibility"] = s.eligibility
    if getattr(s, "how_to_apply", None):
        d["how_to_apply"] = s.how_to_apply
    if getattr(s, "faq", None):
        d["faq"] = s.faq
    if getattr(s, "glossary", None):
        d["glossary"] = s.glossary
    return d


def build_policy_id(source: str, published_at: str, url: str) -> str:
    date_part = published_at[:10]
    url_short = hashlib.md5(url.encode()).hexdigest()[:8]
    source_part = source[:10]
    return f"{source_part}-{date_part}-{url_short}"


def publish_policy(item: PolicyItem, docs_root: str = DOCS_ROOT) -> None:
    policies_dir = Path(docs_root) / "policies"
    policies_dir.mkdir(parents=True, exist_ok=True)

    data = {
        "id": item.id,
        "category": item.category,
        "subcategory": item.subcategory,
        "title": item.title,
        "source": item.source,
        "source_url": item.source_url,
        "file_url": item.file_url,
        "file_type": item.file_type,
        "published_at": item.published_at,
        "crawled_at": item.crawled_at,
        "summary": _summary_dict(item.summary) if item.summary else None,
    }

    out_path = policies_dir / f"{item.id}.json"
    out_path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")

    _update_category_index(item, docs_root)
    _update_archive_index(item, docs_root)


def update_index(item: PolicyItem, docs_root: str = DOCS_ROOT) -> None:
    index_path = Path(docs_root) / "policies" / "index.json"
    if index_path.exists():
        index = json.loads(index_path.read_text(encoding="utf-8"))
    else:
        index = {"items": [], "total": 0, "updated_at": ""}

    entry = {
        "id": item.id,
        "category": item.category,
        "subcategory": item.subcategory,
        "title": item.title,
        "source": item.source,
        "published_at": item.published_at,
        "summary_preview": (item.summary.what_changed[:100] + "...") if item.summary else "",
    }

    all_items = [entry] + [i for i in index["items"] if i["id"] != item.id]
    all_items.sort(key=lambda x: x["published_at"], reverse=True)
    index["items"] = all_items[:50]
    index["total"] = len(index["items"])
    index["updated_at"] = datetime.now(KST).isoformat()

    index_path.write_text(json.dumps(index, ensure_ascii=False, indent=2), encoding="utf-8")


def _update_archive_index(item: PolicyItem, docs_root: str = DOCS_ROOT) -> None:
    year = int(item.published_at[:4])
    archive_dir = Path(docs_root) / "archive"
    archive_dir.mkdir(parents=True, exist_ok=True)

    year_path = archive_dir / f"{year}.json"
    if year_path.exists():
        data = json.loads(year_path.read_text(encoding="utf-8"))
    else:
        data = {"year": year, "total": 0, "updated_at": "", "items": []}

    entry = {
        "id": item.id,
        "category": item.category,
        "subcategory": item.subcategory,
        "title": item.title,
        "source": item.source,
        "published_at": item.published_at,
        "summary_preview": (item.summary.what_changed[:100] + "...") if item.summary else "",
    }
    items = [entry] + [i for i in data["items"] if i["id"] != item.id]
    items.sort(key=lambda x: x["published_at"], reverse=True)
    data["items"] = items
    data["total"] = len(items)
    data["updated_at"] = datetime.now(KST).isoformat()
    year_path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")

    index_path = archive_dir / "index.json"
    if index_path.exists():
        idx = json.loads(index_path.read_text(encoding="utf-8"))
    else:
        idx = {"years": [], "updated_at": ""}
    if year not in idx["years"]:
        idx["years"] = sorted(set(idx["years"] + [year]), reverse=True)
    idx["updated_at"] = datetime.now(KST).isoformat()
    index_path.write_text(json.dumps(idx, ensure_ascii=False, indent=2), encoding="utf-8")


def _update_category_index(item: PolicyItem, docs_root: str) -> None:
    cat_dir = Path(docs_root) / "categories" / item.subcategory
    cat_dir.mkdir(parents=True, exist_ok=True)
    index_path = cat_dir / "index.json"

    if index_path.exists():
        index = json.loads(index_path.read_text(encoding="utf-8"))
    else:
        index = {"subcategory": item.subcategory, "items": []}

    entry = {"id": item.id, "title": item.title, "published_at": item.published_at}
    index["items"] = [entry] + [i for i in index["items"] if i["id"] != item.id]
    index["items"] = index["items"][:30]

    index_path.write_text(json.dumps(index, ensure_ascii=False, indent=2), encoding="utf-8")
