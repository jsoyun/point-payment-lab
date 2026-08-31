package com.paymentlab.voucher.payment.application;

import com.paymentlab.voucher.payment.api.PointPaymentController.PointPaymentResponse;

public record RedisPaymentResult(
        PointPaymentResponse response,
        int httpStatus,
        boolean replayed,
        String source
) {
    public RedisPaymentResult replayedFrom(String replaySource) {
        return new RedisPaymentResult(response, httpStatus, true, replaySource);
    }
}
