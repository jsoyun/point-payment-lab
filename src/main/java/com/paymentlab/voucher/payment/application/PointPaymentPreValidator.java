package com.paymentlab.voucher.payment.application;

import org.springframework.stereotype.Component;

@Component
public class PointPaymentPreValidator {

    public void validate(long requestedPoint, long sellPrice, long balance, long usableLotTotal) {
        if (requestedPoint != sellPrice) {
            throw new PointPaymentValidationException(
                    "PAYMENT_AMOUNT_MISMATCH",
                    "requested point must match voucher product price"
            );
        }
        if (balance < requestedPoint) {
            throw new PointPaymentValidationException(
                    "INSUFFICIENT_POINT_BALANCE",
                    "available point balance is insufficient"
            );
        }
        if (usableLotTotal < requestedPoint) {
            throw new PointPaymentValidationException(
                    "INSUFFICIENT_USABLE_POINT_LOTS",
                    "available and unexpired point lots are insufficient"
            );
        }
    }
}
