import json
import os

import firebase_admin
from firebase_admin import credentials, firestore

from pipeline.models import PolicyItem


def _load_service_account(raw: str) -> dict:
    """FIREBASE_SERVICE_ACCOUNT 시크릿을 파싱한다.

    값에 UTF-8 BOM이나 앞뒤 공백이 섞이면 json.loads가
    'Unexpected UTF-8 BOM' 에러를 내므로 먼저 제거한다.
    """
    return json.loads(raw.lstrip("﻿").strip())


def _get_db(db=None):
    if db is not None:
        return db
    if not firebase_admin._apps:
        service_account = _load_service_account(os.environ["FIREBASE_SERVICE_ACCOUNT"])
        cred = credentials.Certificate(service_account)
        firebase_admin.initialize_app(cred)
    return firestore.client()


def notify_new_policy(item: PolicyItem, batch: str, db=None) -> None:
    """Firestore new_policies/{id} 에 문서를 생성해 Cloud Function을 트리거한다."""
    client = _get_db(db)
    client.collection("new_policies").document(item.id).set({
        "category": item.category,
        "subcategory": item.subcategory,
        "title": item.title,
        "batch": batch,  # "morning" | "evening"
    })
