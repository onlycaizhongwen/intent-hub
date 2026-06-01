package com.intenthub.domain.recognition;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record Envelope(
        String tenantId,
        String source,
        String channel,
        InputType inputType,
        String text,
        String requestId,
        String traceId,
        String sessionId,
        Instant timestamp,
        Map<String, String> metadata,
        List<String> attachments
) {
    public Envelope {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}
