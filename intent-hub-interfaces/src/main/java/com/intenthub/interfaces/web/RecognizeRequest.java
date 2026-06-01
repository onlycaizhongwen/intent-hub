package com.intenthub.interfaces.web;

import com.intenthub.domain.recognition.InputType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RecognizeRequest(
        @NotBlank String tenantId,
        @NotBlank String source,
        @NotBlank String channel,
        @NotNull InputType inputType,
        @NotBlank String text,
        @NotBlank String requestId,
        String traceId,
        String sessionId,
        Instant timestamp,
        Map<String, String> metadata,
        List<String> attachments
) {
}
