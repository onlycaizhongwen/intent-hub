package com.intenthub.domain.recognition;

public record DownstreamAction(
        String actionCode,
        String actionType,
        String target,
        boolean idempotencyRequired,
        int timeoutMs
) {
    public static DownstreamAction none() {
        return new DownstreamAction("NONE", "NONE", "", false, 0);
    }
}
