package com.intenthub.interfaces.error;

public record ApiErrorResponse(
        String code,
        String message,
        int status
) {
}
