package com.intenthub.infrastructure.model;

public record ModelRecognitionRequest(
        String text,
        String sceneId
) {
}
