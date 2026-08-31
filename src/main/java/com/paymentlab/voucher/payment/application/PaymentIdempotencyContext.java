package com.paymentlab.voucher.payment.application;

public record PaymentIdempotencyContext(
        String clientId,
        String httpMethod,
        String apiPath,
        String idempotencyKey,
        String requestHash
) {
    public String scope() {
        return clientId + "|" + httpMethod + "|" + apiPath + "|" + idempotencyKey;
    }
}
