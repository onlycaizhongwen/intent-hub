package com.intenthub.interfaces.admin;

public record BadCaseAnnotationRequest(
        String correctedIntentCode,
        String note,
        String actor
) {
}
