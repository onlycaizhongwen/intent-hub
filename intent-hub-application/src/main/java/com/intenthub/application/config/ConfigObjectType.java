package com.intenthub.application.config;

import java.util.Locale;

public enum ConfigObjectType {
    INTENT,
    SLOT,
    SYNONYM,
    STRATEGY,
    ROUTE,
    DOWNSTREAM_ACTION;

    public static ConfigObjectType fromPath(String value) {
        String normalized = value == null ? "" : value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "INTENT", "INTENTS" -> INTENT;
            case "SLOT", "SLOTS" -> SLOT;
            case "SYNONYM", "SYNONYMS" -> SYNONYM;
            case "STRATEGY", "STRATEGIES", "NLU_STRATEGY", "NLU_STRATEGIES" -> STRATEGY;
            case "ROUTE", "ROUTES", "ROUTING_RULE", "ROUTING_RULES" -> ROUTE;
            case "DOWNSTREAM_ACTION", "DOWNSTREAM_ACTIONS", "ACTION", "ACTIONS" -> DOWNSTREAM_ACTION;
            default -> throw new IllegalArgumentException("unsupported config object type: " + value);
        };
    }
}
