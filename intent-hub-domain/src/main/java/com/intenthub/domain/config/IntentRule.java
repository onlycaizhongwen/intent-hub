package com.intenthub.domain.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record IntentRule(
        String intentCode,
        String matchType,
        String pattern,
        double confidence,
        String explanation,
        Map<String, String> fixedSlots
) {
    public IntentRule {
        fixedSlots = fixedSlots == null ? Map.of() : Map.copyOf(fixedSlots);
    }

    public boolean matches(String text) {
        return switch (matchType) {
            case "EXACT" -> pattern.equals(text);
            case "CONTAINS" -> text.contains(pattern);
            case "REGEX" -> Pattern.compile(pattern).matcher(text).find();
            default -> false;
        };
    }

    public Map<String, String> extractSlots(String text) {
        Map<String, String> slots = new LinkedHashMap<>(fixedSlots);
        if ("REGEX".equals(matchType)) {
            Matcher matcher = Pattern.compile(pattern).matcher(text);
            if (matcher.find()) {
                for (int i = 1; i <= matcher.groupCount(); i++) {
                    slots.put("group_" + i, matcher.group(i));
                }
                if ("ORDER_CANCEL".equals(intentCode) && matcher.groupCount() >= 1) {
                    slots.put("order_id", matcher.group(1));
                }
            }
        }
        return slots;
    }
}
