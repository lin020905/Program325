package org.xidian.program325.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessError(ApiException error) {
        return response(error.status(), error.code(), error.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            ServletRequestBindingException.class})
    public ResponseEntity<ApiResponse<Void>> handleRequestError() {
        return response(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "请求参数缺失或格式错误");
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<ApiResponse<Void>> handleStorageError(RuntimeException error) {
        LOGGER.error("Database operation failed", error);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "DATA_ACCESS_ERROR", "数据操作失败，请稍后重试");
    }

    private ResponseEntity<ApiResponse<Void>> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiResponse<>(code, message, null));
    }
}
