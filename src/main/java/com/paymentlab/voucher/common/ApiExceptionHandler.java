package com.paymentlab.voucher.common;

import java.time.LocalDateTime;
import com.paymentlab.voucher.payment.application.PaymentAttemptConflictException;
import com.paymentlab.voucher.payment.application.PointPaymentValidationException;
import com.paymentlab.voucher.provider.ProviderIssueConflictException;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(PaymentAttemptConflictException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentAttemptConflict(
            PaymentAttemptConflictException error
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "timestamp", LocalDateTime.now().toString(),
                        "status", HttpStatus.CONFLICT.value(),
                        "code", error.getCode(),
                        "message", error.getMessage()
                ));
    }

    @ExceptionHandler(ProviderIssueConflictException.class)
    public ResponseEntity<Map<String, Object>> handleProviderIssueConflict(
            ProviderIssueConflictException error
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "timestamp", LocalDateTime.now().toString(),
                        "status", HttpStatus.CONFLICT.value(),
                        "code", error.getCode(),
                        "message", error.getMessage()
                ));
    }

    @ExceptionHandler(PointPaymentValidationException.class)
    public ResponseEntity<Map<String, Object>> handlePointPaymentValidation(
            PointPaymentValidationException error
    ) {
        return ResponseEntity.unprocessableEntity()
                .body(Map.of(
                        "timestamp", LocalDateTime.now().toString(),
                        "status", HttpStatus.UNPROCESSABLE_ENTITY.value(),
                        "code", error.getCode(),
                        "message", error.getMessage()
                ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException error) {
        return error(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException error) {
        return error(HttpStatus.CONFLICT, "DB unique/constraint violation: " + rootMessage(error));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException error) {
        return error(HttpStatus.BAD_REQUEST, "invalid request");
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException error) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, error.getMessage());
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(Map.of(
                        "timestamp", LocalDateTime.now().toString(),
                        "status", status.value(),
                        "message", message
                ));
    }

    private String rootMessage(Throwable error) {
        Throwable cursor = error;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage();
    }
}
