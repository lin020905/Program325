package org.xidian.program325.common;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static ApiException missing(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
