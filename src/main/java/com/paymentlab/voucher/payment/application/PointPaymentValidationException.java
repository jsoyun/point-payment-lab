package com.paymentlab.voucher.payment.application;

public class PointPaymentValidationException extends RuntimeException {

    private final String code;

    public PointPaymentValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
