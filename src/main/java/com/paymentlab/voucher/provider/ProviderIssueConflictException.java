package com.paymentlab.voucher.provider;

public class ProviderIssueConflictException extends RuntimeException {

    private final String code;

    public ProviderIssueConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
