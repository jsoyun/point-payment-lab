package com.paymentlab.voucher.payment.application;

import org.springframework.http.HttpStatus;

public class PaymentAttemptConflictException extends RuntimeException {

    private final String code;
    private final HttpStatus httpStatus;

    public PaymentAttemptConflictException(String code, String message) {
        this(HttpStatus.CONFLICT, code, message);
    }

    public PaymentAttemptConflictException(HttpStatus httpStatus, String code, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
