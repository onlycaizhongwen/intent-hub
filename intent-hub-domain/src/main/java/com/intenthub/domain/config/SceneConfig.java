package com.intenthub.domain.config;

import com.intenthub.domain.recognition.DownstreamAction;

import java.util.List;
import java.util.Map;

public record SceneConfig(
        String tenantId,
        String sceneId,
        String version,
        double rejectThreshold,
        List<IntentRule> rules,
        Map<String, List<String>> requiredSlots,
        Map<String, DownstreamAction> downstreamActions,
        ModelPolicy modelPolicy,
        LlmPolicy llmPolicy
) {
    public SceneConfig {
        rules = rules == null ? List.of() : List.copyOf(rules);
        requiredSlots = requiredSlots == null ? Map.of() : Map.copyOf(requiredSlots);
        downstreamActions = downstreamActions == null ? Map.of() : Map.copyOf(downstreamActions);
        modelPolicy = modelPolicy == null ? ModelPolicy.enabledByDefault() : modelPolicy;
        llmPolicy = llmPolicy == null ? LlmPolicy.disabled() : llmPolicy;
    }

    public List<String> requiredSlotsFor(String intentCode) {
        return requiredSlots.getOrDefault(intentCode, List.of());
    }

    public DownstreamAction actionFor(String intentCode) {
        return downstreamActions.getOrDefault(intentCode, DownstreamAction.none());
    }
}
