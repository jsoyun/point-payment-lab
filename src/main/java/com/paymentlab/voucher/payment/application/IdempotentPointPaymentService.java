package com.paymentlab.voucher.payment.application;

import com.paymentlab.voucher.payment.api.PointPaymentController.PointPaymentRequest;
import com.paymentlab.voucher.payment.api.PointPaymentController.PointPaymentResponse;
import com.paymentlab.voucher.payment.domain.PaymentAttempt;
import com.paymentlab.voucher.payment.domain.repository.PaymentAttemptRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class IdempotentPointPaymentService {

    private final LegacyPointPaymentService legacyPointPaymentService;
    private final PaymentAttemptWriter paymentAttemptWriter;
    private final PaymentAttemptRepository paymentAttemptRepository;

    public IdempotentPointPaymentService(
            LegacyPointPaymentService legacyPointPaymentService,
            PaymentAttemptWriter paymentAttemptWriter,
            PaymentAttemptRepository paymentAttemptRepository
    ) {
        this.legacyPointPaymentService = legacyPointPaymentService;
        this.paymentAttemptWriter = paymentAttemptWriter;
        this.paymentAttemptRepository = paymentAttemptRepository;
    }

    public IdempotentPaymentResult pay(PointPaymentRequest request) {
        try {
            paymentAttemptWriter.claim(request);
        } catch (DataIntegrityViolationException duplicateOrderId) {
            return handleExisting(request, duplicateOrderId);
        }

        try {
            PointPaymentResponse response = legacyPointPaymentService.pay(request);
            paymentAttemptWriter.markSucceeded(request.orderId(), response);
            return new IdempotentPaymentResult(response, false);
        } catch (RuntimeException paymentFailure) {
            paymentAttemptWriter.markFailed(request.orderId(), paymentFailure);
            throw paymentFailure;
        }
    }

    private IdempotentPaymentResult handleExisting(
            PointPaymentRequest request,
            DataIntegrityViolationException duplicateOrderId
    ) {
        PaymentAttempt existing = paymentAttemptRepository.findByOrderId(request.orderId())
                .orElseThrow(() -> duplicateOrderId);

        if (!existing.matches(request)) {
            throw new PaymentAttemptConflictException(
                    "IDEMPOTENCY_KEY_REUSED",
                    "orderId was already used with a different payment request"
            );
        }
        if (existing.isSucceeded()) {
            return new IdempotentPaymentResult(existing.toResponse(), true);
        }
        if (existing.isProcessing()) {
            throw new PaymentAttemptConflictException(
                    "PAYMENT_PROCESSING",
                    "payment with this orderId is already processing"
            );
        }
        if (existing.isFailed()) {
            throw new PaymentAttemptConflictException(
                    "PAYMENT_FAILED",
                    "previous payment with this orderId failed; use a new orderId or retry policy"
            );
        }
        throw new PaymentAttemptConflictException(
                "UNKNOWN_PAYMENT_STATUS",
                "payment attempt has an unsupported status"
        );
    }

    public record IdempotentPaymentResult(PointPaymentResponse response, boolean replayed) {
    }
}
