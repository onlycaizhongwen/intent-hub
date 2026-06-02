package com.intenthub.infrastructure.llm;

import java.util.Map;

public record LlmRecognitionResponse(
        String intentCode,
        double confidence,
        Map<String, String> slots,
        String explanation
) {
}
