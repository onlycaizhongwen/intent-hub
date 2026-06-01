package com.intenthub.domain.conversation;

public record Conversation(
        String sessionId,
        String tenantId,
        String sceneId
) {
}
