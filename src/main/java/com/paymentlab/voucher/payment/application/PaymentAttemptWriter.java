package com.paymentlab.voucher.payment.application;

import com.paymentlab.voucher.payment.api.PointPaymentController.PointPaymentRequest;
import com.paymentlab.voucher.payment.api.PointPaymentController.PointPaymentResponse;
import com.paymentlab.voucher.payment.domain.PaymentAttempt;
import com.paymentlab.voucher.payment.domain.repository.PaymentAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
public class PaymentAttemptWriter {

    private final PaymentAttemptRepository repository;

    public PaymentAttemptWriter(PaymentAttemptRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentAttempt claim(PointPaymentRequest request) {
        return repository.saveAndFlush(PaymentAttempt.processing(request));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentAttempt claim(
            PointPaymentRequest request,
            PaymentIdempotencyContext context,
            LocalDateTime expiresAt
    ) {
        return repository.saveAndFlush(PaymentAttempt.processing(request, context, expiresAt));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSucceeded(String orderId, PointPaymentResponse response) {
        PaymentAttempt attempt = find(orderId);
        attempt.succeed(response);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSucceeded(
            String orderId,
            PointPaymentResponse response,
            int httpStatus,
            String responseBody
    ) {
        PaymentAttempt attempt = find(orderId);
        attempt.succeed(response, httpStatus, responseBody);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String orderId, Throwable failure) {
        PaymentAttempt attempt = find(orderId);
        attempt.fail(failure.getMessage());
    }

    private PaymentAttempt find(String orderId) {
        return repository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("payment attempt not found"));
    }
}
