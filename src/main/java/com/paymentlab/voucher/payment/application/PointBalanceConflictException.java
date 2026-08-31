package com.paymentlab.voucher.payment.application;

public class PointBalanceConflictException extends RuntimeException {

    private final String code;

    public PointBalanceConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
