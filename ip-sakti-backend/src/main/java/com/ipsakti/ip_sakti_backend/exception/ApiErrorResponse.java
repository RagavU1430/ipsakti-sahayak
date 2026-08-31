package com.ipsakti.ip_sakti_backend.exception;

import java.time.Instant;

public record ApiErrorResponse(
        String error,
        String code,
        String detail,
        Instant timestamp
) {
    public static ApiErrorResponse of(String error, String code, String detail) {
        return new ApiErrorResponse(error, code, detail, Instant.now());
    }
}
