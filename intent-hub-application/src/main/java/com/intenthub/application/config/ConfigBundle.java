package com.intenthub.application.config;

import java.util.List;
import java.util.Map;

public record ConfigBundle(
        ConfigVersionInfo version,
        List<Map<String, Object>> intents,
        List<Map<String, Object>> slots,
        List<Map<String, Object>> synonyms,
        List<Map<String, Object>> strategies,
        List<Map<String, Object>> routes,
        List<Map<String, Object>> downstreamActions
) {
    public ConfigBundle {
        intents = intents == null ? List.of() : List.copyOf(intents);
        slots = slots == null ? List.of() : List.copyOf(slots);
        synonyms = synonyms == null ? List.of() : List.copyOf(synonyms);
        strategies = strategies == null ? List.of() : List.copyOf(strategies);
        routes = routes == null ? List.of() : List.copyOf(routes);
        downstreamActions = downstreamActions == null ? List.of() : List.copyOf(downstreamActions);
    }
}
