import hashlib
import json
from datetime import datetime, timezone, timedelta
from pathlib import Path

from pipeline.models import PolicyItem, PolicySummary

DOCS_ROOT = "docs"
KST = timezone(timedelta(hours=9))


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
        "summary": {
            "what_changed": item.summary.what_changed,
            "who_is_affected": item.summary.who_is_affected,
            "when_effective": item.summary.when_effective,
            "key_points": item.summary.key_points,
        } if item.summary else None,
    }

    out_path = policies_dir / f"{item.id}.json"
    out_path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")

    _update_category_index(item, docs_root)


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
