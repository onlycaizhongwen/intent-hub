package com.intenthub.infrastructure.llm;

public record LlmRecognitionRequest(
        String text,
        String sceneId,
        String provider,
        String model
) {
}
