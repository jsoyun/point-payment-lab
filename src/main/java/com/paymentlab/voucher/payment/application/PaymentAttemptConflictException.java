package com.paymentlab.voucher.payment.application;

public class PaymentAttemptConflictException extends RuntimeException {

    private final String code;

    public PaymentAttemptConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
