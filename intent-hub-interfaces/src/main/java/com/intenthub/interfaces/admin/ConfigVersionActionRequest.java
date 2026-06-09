package com.intenthub.interfaces.admin;

import java.util.List;

public record ConfigVersionActionRequest(
        String actor,
        String reason,
        String expectedSnapshotHash,
        List<String> roles
) {
    public ConfigVersionActionRequest(String actor) {
        this(actor, null, null, null);
    }

    public ConfigVersionActionRequest(String actor, String reason) {
        this(actor, reason, null, null);
    }

    public ConfigVersionActionRequest(String actor, String reason, String expectedSnapshotHash) {
        this(actor, reason, expectedSnapshotHash, null);
    }

    String normalizedActor() {
        return actor == null || actor.isBlank() ? "system" : actor;
    }

    String normalizedReason() {
        return reason == null || reason.isBlank() ? "not provided" : reason;
    }

    String normalizedExpectedSnapshotHash() {
        return expectedSnapshotHash == null || expectedSnapshotHash.isBlank() ? null : expectedSnapshotHash;
    }

    List<String> normalizedRoles() {
        return roles == null ? List.of() : roles;
    }
}
