from typing import Any

from fastapi import FastAPI
from pydantic import BaseModel, Field


app = FastAPI(title="Intent Hub Model Service Example", version="0.1.0")


class RecognizeRequest(BaseModel):
    text: str = Field(default="")
    sceneId: str = Field(default="")


class RecognizeResponse(BaseModel):
    intentCode: str | None = None
    confidence: float | None = None
    slots: dict[str, Any] = Field(default_factory=dict)
    explanation: str | None = None


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


@app.post("/recognize", response_model=RecognizeResponse)
def recognize(request: RecognizeRequest) -> RecognizeResponse:
    text = request.text.strip()
    normalized = text.lower()

    if contains_any(text, normalized, ("取消", "撤销", "cancel")):
        return RecognizeResponse(
            intentCode="ORDER_CANCEL",
            confidence=0.86,
            slots={"order_id": extract_order_id(text)},
            explanation="fastapi example matched order cancel",
        )

    if contains_any(text, normalized, ("查询", "查看", "order", "status")):
        return RecognizeResponse(
            intentCode="ORDER_QUERY",
            confidence=0.82,
            slots={"order_id": extract_order_id(text)},
            explanation="fastapi example matched order query",
        )

    return RecognizeResponse(explanation="fastapi example did not match")


def contains_any(original: str, normalized: str, keywords: tuple[str, ...]) -> bool:
    return any(keyword in original or keyword in normalized for keyword in keywords)


def extract_order_id(text: str) -> str | None:
    for token in text.replace(",", " ").split():
        cleaned = token.strip()
        if any(char.isdigit() for char in cleaned):
            return cleaned
    return None
