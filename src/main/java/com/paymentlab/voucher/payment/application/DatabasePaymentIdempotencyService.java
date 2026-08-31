package com.paymentlab.voucher.payment.application;

import com.paymentlab.voucher.payment.api.PointPaymentController.PointPaymentRequest;
import com.paymentlab.voucher.payment.api.PointPaymentController.PointPaymentResponse;
import com.paymentlab.voucher.payment.config.RedisIdempotencyProperties;
import com.paymentlab.voucher.payment.domain.PaymentAttempt;
import com.paymentlab.voucher.payment.domain.repository.PaymentAttemptRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DatabasePaymentIdempotencyService {

    private static final int CREATED = 201;

    private final LegacyPointPaymentService legacyPointPaymentService;
    private final PaymentAttemptWriter paymentAttemptWriter;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentResponseCodec responseCodec;
    private final RedisIdempotencyProperties properties;

    public DatabasePaymentIdempotencyService(
            LegacyPointPaymentService legacyPointPaymentService,
            PaymentAttemptWriter paymentAttemptWriter,
            PaymentAttemptRepository paymentAttemptRepository,
            PaymentResponseCodec responseCodec,
            RedisIdempotencyProperties properties
    ) {
        this.legacyPointPaymentService = legacyPointPaymentService;
        this.paymentAttemptWriter = paymentAttemptWriter;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.responseCodec = responseCodec;
        this.properties = properties;
    }

    public RedisPaymentResult pay(
            PointPaymentRequest request,
            PaymentIdempotencyContext context
    ) {
        Optional<PaymentAttempt> scoped = findByScope(context);
        if (scoped.isPresent()) {
            return resolveScoped(scoped.get(), context);
        }

        try {
            paymentAttemptWriter.claim(
                    request,
                    context,
                    LocalDateTime.now().plus(properties.resultTtl())
            );
        } catch (DataIntegrityViolationException duplicate) {
            return resolveDuplicate(request, context, duplicate);
        }

        try {
            PointPaymentResponse response = legacyPointPaymentService.payWithConditionalDebit(request);
            String responseBody = responseCodec.serialize(response);
            paymentAttemptWriter.markSucceeded(
                    request.orderId(), response, CREATED, responseBody
            );
            return new RedisPaymentResult(response, CREATED, false, "DATABASE_CREATED");
        } catch (RuntimeException paymentFailure) {
            paymentAttemptWriter.markFailed(request.orderId(), paymentFailure);
            throw paymentFailure;
        }
    }

    public Optional<RedisPaymentResult> findCompleted(PaymentIdempotencyContext context) {
        return findByScope(context)
                .filter(PaymentAttempt::isSucceeded)
                .map(attempt -> completedResult(attempt, context, "DATABASE_REPLAY"));
    }

    private RedisPaymentResult resolveDuplicate(
            PointPaymentRequest request,
            PaymentIdempotencyContext context,
            DataIntegrityViolationException duplicate
    ) {
        Optional<PaymentAttempt> scoped = findByScope(context);
        if (scoped.isPresent()) {
            return resolveScoped(scoped.get(), context);
        }

        PaymentAttempt byOrder = paymentAttemptRepository.findByOrderId(request.orderId())
                .orElseThrow(() -> duplicate);
        if (!byOrder.matches(request)) {
            throw reusedKey("orderId was already used with a different payment request");
        }
        if (byOrder.isSucceeded()) {
            return completedResult(byOrder, null, "DATABASE_ORDER_REPLAY");
        }
        return resolveStatus(byOrder);
    }

    private RedisPaymentResult resolveScoped(
            PaymentAttempt attempt,
            PaymentIdempotencyContext context
    ) {
        if (!attempt.matches(context)) {
            throw reusedKey("Idempotency-Key was already used with a different payment request");
        }
        if (attempt.isSucceeded()) {
            return completedResult(attempt, context, "DATABASE_REPLAY");
        }
        return resolveStatus(attempt);
    }

    private RedisPaymentResult resolveStatus(PaymentAttempt attempt) {
        if (attempt.isProcessing()) {
            throw new PaymentAttemptConflictException(
                    "PAYMENT_PROCESSING",
                    "payment with this Idempotency-Key is already processing"
            );
        }
        if (attempt.isFailed()) {
            throw new PaymentAttemptConflictException(
                    "PAYMENT_FAILED",
                    "previous payment with this Idempotency-Key failed"
            );
        }
        throw new PaymentAttemptConflictException(
                "UNKNOWN_PAYMENT_STATUS",
                "payment attempt has an unsupported status"
        );
    }

    private RedisPaymentResult completedResult(
            PaymentAttempt attempt,
            PaymentIdempotencyContext context,
            String source
    ) {
        if (context != null && !attempt.matches(context)) {
            throw reusedKey("Idempotency-Key was already used with a different payment request");
        }
        if (attempt.getHttpStatus() != null && attempt.getResponseBody() != null) {
            return new RedisPaymentResult(
                    responseCodec.deserialize(attempt.getResponseBody()),
                    attempt.getHttpStatus(),
                    true,
                    source
            );
        }
        return new RedisPaymentResult(attempt.toResponse(), 200, true, source);
    }

    private Optional<PaymentAttempt> findByScope(PaymentIdempotencyContext context) {
        return paymentAttemptRepository
                .findByClientIdAndHttpMethodAndApiPathAndIdempotencyKey(
                        context.clientId(),
                        context.httpMethod(),
                        context.apiPath(),
                        context.idempotencyKey()
                );
    }

    private PaymentAttemptConflictException reusedKey(String message) {
        return new PaymentAttemptConflictException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "IDEMPOTENCY_KEY_REUSED",
                message
        );
    }
}
