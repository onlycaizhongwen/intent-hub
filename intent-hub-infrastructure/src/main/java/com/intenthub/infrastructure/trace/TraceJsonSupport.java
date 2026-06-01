package com.intenthub.infrastructure.trace;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class TraceJsonSupport {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TraceJsonSupport() {
    }

    static String map(Map<String, String> values) {
        StringBuilder json = new StringBuilder("{");
        Iterator<Map.Entry<String, String>> iterator = values.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            json.append(quote(entry.getKey())).append(':').append(quote(entry.getValue()));
            if (iterator.hasNext()) {
                json.append(',');
            }
        }
        return json.append('}').toString();
    }

    static String list(List<String> values) {
        StringBuilder json = new StringBuilder("[");
        Iterator<String> iterator = values.iterator();
        while (iterator.hasNext()) {
            json.append(quote(iterator.next()));
            if (iterator.hasNext()) {
                json.append(',');
            }
        }
        return json.append(']').toString();
    }

    static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder escaped = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (current < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) current));
                    } else {
                        escaped.append(current);
                    }
                }
            }
        }
        return escaped.append('"').toString();
    }

    static Map<String, Object> objectMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            if (!node.isObject()) {
                return Map.of();
            }
            return OBJECT_MAPPER.convertValue(node, Map.class);
        } catch (RuntimeException ex) {
            return Map.of();
        }
    }

    static Map<String, String> stringMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            if (!node.isObject()) {
                return Map.of();
            }
            return OBJECT_MAPPER.convertValue(node, Map.class);
        } catch (RuntimeException ex) {
            return Map.of();
        }
    }

    static List<String> stringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            if (!node.isArray()) {
                return List.of();
            }
            return OBJECT_MAPPER.convertValue(node, List.class);
        } catch (RuntimeException ex) {
            return List.of();
        }
    }
}
