package com.intenthub.infrastructure.trace;

import com.intenthub.domain.recognition.DownstreamAction;
import com.intenthub.domain.recognition.Envelope;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class IdempotencyKeyGenerator {
    private IdempotencyKeyGenerator() {
    }

    static String generate(Envelope envelope, DownstreamAction action) {
        return digest(envelope.tenantId() + "|" + envelope.requestId() + "|" + action.actionCode());
    }

    static String requestHash(Envelope envelope, DownstreamAction action) {
        return digest(envelope.tenantId() + "|" + envelope.source() + "|" + envelope.channel() + "|"
                + envelope.inputType() + "|" + envelope.requestId() + "|" + envelope.text() + "|"
                + action.actionCode() + "|" + action.target());
    }

    private static String digest(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
