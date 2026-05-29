import json
import os
from typing import Optional

import firebase_admin
from firebase_admin import credentials, firestore

from pipeline.models import PolicyItem


def _get_db(db=None):
    if db is not None:
        return db
    if not firebase_admin._apps:
        service_account = json.loads(os.environ["FIREBASE_SERVICE_ACCOUNT"])
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
