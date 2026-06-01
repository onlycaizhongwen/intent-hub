package com.intenthub.infrastructure.trace;

final class SensitiveDataMasker {
    private SensitiveDataMasker() {
    }

    static String maskText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String masked = text.replaceAll("(?<!\\d)(1[3-9]\\d)\\d{4}(\\d{4})(?!\\d)", "$1****$2");
        masked = masked.replaceAll("(?i)([A-Z0-9._%+-])[A-Z0-9._%+-]*(@[A-Z0-9.-]+\\.[A-Z]{2,})", "$1***$2");
        return masked;
    }
}
