package com.intenthub.infrastructure.trace;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class TraceJsonSupport {
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
}
