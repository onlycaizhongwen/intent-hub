package com.intenthub.infrastructure.model;

import java.util.Map;

public record ModelRecognitionResponse(
        String intentCode,
        Double confidence,
        Map<String, String> slots,
        String explanation
) {
}
