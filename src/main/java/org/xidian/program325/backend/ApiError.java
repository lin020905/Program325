package org.xidian.program325.backend;

import org.springframework.http.HttpStatus;

final class ApiError extends RuntimeException {
    final HttpStatus status;
    final int code;

    ApiError(HttpStatus status, String message) {
        this(status, status.value(), message);
    }

    ApiError(HttpStatus status, int code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    static void require(boolean valid, HttpStatus status, String message) {
        if (!valid) {
            throw new ApiError(status, message);
        }
    }
}
