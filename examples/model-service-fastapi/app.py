import os
import re
from typing import Any

from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field


MODEL_VERSION = "fastapi-example-2026-06-08"
DEFAULT_THRESHOLD = 0.70
AUTH_TOKEN = os.getenv("MODEL_SERVICE_AUTH_TOKEN", "").strip()
AUTH_TOKEN_FILE = os.getenv("MODEL_SERVICE_AUTH_TOKEN_FILE", "").strip()

app = FastAPI(title="Intent Hub Model Service Example", version="0.2.0")


class RecognizeRequest(BaseModel):
    text: str = Field(default="")
    sceneId: str = Field(default="")


class RecognizeResponse(BaseModel):
    intentCode: str | None = None
    confidence: float | None = None
    slots: dict[str, Any] = Field(default_factory=dict)
    explanation: str | None = None
    modelVersion: str = MODEL_VERSION
    threshold: float = DEFAULT_THRESHOLD


@app.get("/health")
def health() -> dict[str, str | float]:
    return {
        "status": "UP",
        "modelVersion": MODEL_VERSION,
        "threshold": DEFAULT_THRESHOLD,
    }


@app.post("/recognize", response_model=RecognizeResponse)
def recognize(request: RecognizeRequest, authorization: str | None = Header(default=None)) -> RecognizeResponse:
    require_authorization(authorization)
    text = request.text.strip()
    normalized = text.lower()

    candidates = [
        match_order_cancel(text, normalized),
        match_refund_apply(text, normalized),
        match_logistics_query(text, normalized),
        match_invoice_apply(text, normalized),
        match_order_query(text, normalized),
    ]
    candidates = [candidate for candidate in candidates if candidate is not None]
    if not candidates:
        return RecognizeResponse(explanation="fastapi example did not match")
    return max(candidates, key=lambda candidate: candidate.confidence or 0.0)


def require_authorization(authorization: str | None) -> None:
    auth_token = current_auth_token()
    if not auth_token:
        return
    expected = f"Bearer {auth_token}"
    if authorization != expected:
        raise HTTPException(status_code=401, detail="missing or invalid model service token")


def current_auth_token() -> str:
    if AUTH_TOKEN:
        return AUTH_TOKEN
    if not AUTH_TOKEN_FILE:
        return ""
    try:
        with open(AUTH_TOKEN_FILE, encoding="utf-8") as token_file:
            return token_file.read().strip()
    except OSError:
        return ""


def match_order_cancel(text: str, normalized: str) -> RecognizeResponse | None:
    if not contains_any(text, normalized, ("取消", "撤销", "cancel")):
        return None
    return RecognizeResponse(
        intentCode="ORDER_CANCEL",
        confidence=0.86,
        slots={"order_id": extract_order_id(text)},
        explanation="fastapi example matched order cancel",
    )


def match_order_query(text: str, normalized: str) -> RecognizeResponse | None:
    if not contains_any(text, normalized, ("查询", "查看", "order", "status")):
        return None
    return RecognizeResponse(
        intentCode="ORDER_QUERY",
        confidence=0.82,
        slots={"order_id": extract_order_id(text)},
        explanation="fastapi example matched order query",
    )


def match_refund_apply(text: str, normalized: str) -> RecognizeResponse | None:
    if not contains_any(text, normalized, ("退款", "退钱", "refund")):
        return None
    return RecognizeResponse(
        intentCode="REFUND_APPLY",
        confidence=0.80,
        slots={"order_id": extract_order_id(text), "reason": extract_reason(text)},
        explanation="fastapi example matched refund apply",
    )


def match_logistics_query(text: str, normalized: str) -> RecognizeResponse | None:
    if not contains_any(text, normalized, ("物流", "快递", "shipping", "delivery", "tracking")):
        return None
    return RecognizeResponse(
        intentCode="LOGISTICS_QUERY",
        confidence=0.78,
        slots={"order_id": extract_order_id(text)},
        explanation="fastapi example matched logistics query",
    )


def match_invoice_apply(text: str, normalized: str) -> RecognizeResponse | None:
    if not contains_any(text, normalized, ("发票", "invoice")):
        return None
    return RecognizeResponse(
        intentCode="INVOICE_APPLY",
        confidence=0.76,
        slots={"order_id": extract_order_id(text), "invoice_type": extract_invoice_type(text)},
        explanation="fastapi example matched invoice apply",
    )


def contains_any(original: str, normalized: str, keywords: tuple[str, ...]) -> bool:
    return any(keyword in original or keyword in normalized for keyword in keywords)


def extract_order_id(text: str) -> str | None:
    for token in re.split(r"[\s,，。；;:：]+", text):
        cleaned = token.strip()
        if any(char.isdigit() for char in cleaned):
            return cleaned
    return None


def extract_reason(text: str) -> str | None:
    if "破损" in text:
        return "DAMAGED"
    if "超时" in text or "太慢" in text:
        return "DELAYED"
    if "不想要" in text or "不要了" in text:
        return "NO_LONGER_NEEDED"
    return None


def extract_invoice_type(text: str) -> str | None:
    if "专票" in text or "增值税" in text:
        return "VAT_SPECIAL"
    if "普票" in text or "普通" in text:
        return "VAT_NORMAL"
    return None
