package com.paymentlab.voucher.payment.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PointPaymentPreValidatorTest {

    private final PointPaymentPreValidator validator = new PointPaymentPreValidator();

    @Test
    void acceptsSufficientMatchingPayment() {
        assertThatCode(() -> validator.validate(5000, 5000, 5000, 5000))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAmountDifferentFromProductPrice() {
        assertCode("PAYMENT_AMOUNT_MISMATCH",
                () -> validator.validate(3000, 5000, 10000, 10000));
    }

    @Test
    void rejectsInsufficientBalance() {
        assertCode("INSUFFICIENT_POINT_BALANCE",
                () -> validator.validate(10000, 10000, 5000, 5000));
    }

    @Test
    void rejectsInsufficientUsableLots() {
        assertCode("INSUFFICIENT_USABLE_POINT_LOTS",
                () -> validator.validate(5000, 5000, 5000, 3000));
    }

    private void assertCode(String code, ThrowingCall call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(PointPaymentValidationException.class)
                .extracting(error -> ((PointPaymentValidationException) error).getCode())
                .isEqualTo(code);
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
