package org.xidian.program325.common;

public record ApiResponse<T>(String code, String message, T data) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "成功", data);
    }
}
