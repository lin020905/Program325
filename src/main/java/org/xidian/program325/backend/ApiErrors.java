package org.xidian.program325.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import static org.springframework.http.HttpStatus.*;

@RestControllerAdvice(assignableTypes = BookingApi.class)
public class ApiErrors {
    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

    @ExceptionHandler(ApiError.class)
    ResponseEntity<BookingApi.Result<Void>> business(ApiError error) {
        return ResponseEntity.status(error.status).body(new BookingApi.Result<>(error.code, error.getMessage(), null));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<BookingApi.Result<Void>> invalidInput() {
        return response(BAD_REQUEST, "请求参数或 JSON 格式无效");
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<BookingApi.Result<Void>> duplicate() {
        return response(CONFLICT, "房号已存在或记录已处理");
    }

    @ExceptionHandler(TransientDataAccessException.class)
    ResponseEntity<BookingApi.Result<Void>> retry() {
        return response(SERVICE_UNAVAILABLE, "数据库繁忙，请稍后重试");
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ResponseEntity<BookingApi.Result<Void>> persistence(RuntimeException error) {
        log.error("Booking database operation failed ({})", error.getClass().getSimpleName());
        return response(INTERNAL_SERVER_ERROR, "数据库操作失败");
    }

    private static ResponseEntity<BookingApi.Result<Void>> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new BookingApi.Result<>(status.value(), message, null));
    }
}
