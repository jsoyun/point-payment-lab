package com.paymentlab.voucher.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.paymentlab.voucher.payment.api.PointPaymentController.PointPaymentRequest;
import com.paymentlab.voucher.payment.api.PointPaymentController.PointPaymentResponse;
import com.paymentlab.voucher.payment.config.RedisIdempotencyProperties;
import com.paymentlab.voucher.payment.domain.PaymentAttempt;
import com.paymentlab.voucher.payment.domain.repository.PaymentAttemptRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DatabasePaymentIdempotencyServiceTest {

    @Mock
    private LegacyPointPaymentService legacyPointPaymentService;
    @Mock
    private PaymentAttemptWriter paymentAttemptWriter;
    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;
    @Mock
    private PaymentResponseCodec responseCodec;

    private DatabasePaymentIdempotencyService service;

    private final PointPaymentRequest request = new PointPaymentRequest(
            "ORDER-REDIS-001", "point-wallet-001", 1L, 1L, 5000
    );
    private final PaymentIdempotencyContext context = new PaymentIdempotencyContext(
            "point-payment-lab-mall", "POST",
            RedisIdempotentPointPaymentService.API_PATH, "KEY-001", "request-hash"
    );
    private final PointPaymentResponse response = new PointPaymentResponse(
            "A voucher has been issued successfully",
            "ORDER-REDIS-001", "CP-001", "PIN-001", 5000, "5000"
    );

    @BeforeEach
    void setUp() {
        RedisIdempotencyProperties properties = new RedisIdempotencyProperties(
                true, "redis://127.0.0.1:6379", "point-payment-lab-mall",
                Duration.ofHours(1), Duration.ofMillis(500)
        );
        service = new DatabasePaymentIdempotencyService(
                legacyPointPaymentService,
                paymentAttemptWriter,
                paymentAttemptRepository,
                responseCodec,
                properties
        );
    }

    @Test
    void firstRequestStoresOriginalStatusAndBody() {
        when(paymentAttemptRepository
                .findByClientIdAndHttpMethodAndApiPathAndIdempotencyKey(
                        context.clientId(), context.httpMethod(), context.apiPath(), context.idempotencyKey()
                )).thenReturn(Optional.empty());
        when(legacyPointPaymentService.payWithConditionalDebit(request)).thenReturn(response);
        when(responseCodec.serialize(response)).thenReturn("{\"orderId\":\"ORDER-REDIS-001\"}");

        RedisPaymentResult result = service.pay(request, context);

        assertThat(result.httpStatus()).isEqualTo(201);
        assertThat(result.replayed()).isFalse();
        verify(paymentAttemptWriter).claim(any(), any(), any(LocalDateTime.class));
        verify(paymentAttemptWriter).markSucceeded(
                request.orderId(), response, 201, "{\"orderId\":\"ORDER-REDIS-001\"}"
        );
    }

    @Test
    void completedRequestReplaysOriginalStatusAndBody() {
        PaymentAttempt completed = PaymentAttempt.processing(
                request, context, LocalDateTime.now().plusHours(1)
        );
        completed.succeed(response, 201, "stored-body");
        when(paymentAttemptRepository
                .findByClientIdAndHttpMethodAndApiPathAndIdempotencyKey(
                        context.clientId(), context.httpMethod(), context.apiPath(), context.idempotencyKey()
                )).thenReturn(Optional.of(completed));
        when(responseCodec.deserialize("stored-body")).thenReturn(response);

        RedisPaymentResult result = service.pay(request, context);

        assertThat(result.httpStatus()).isEqualTo(201);
        assertThat(result.response()).isEqualTo(response);
        assertThat(result.replayed()).isTrue();
        verify(legacyPointPaymentService, never()).payWithConditionalDebit(request);
    }

    @Test
    void sameIdempotencyKeyWithDifferentPayloadHashIsRejected() {
        PaymentAttempt existing = PaymentAttempt.processing(
                request, context, LocalDateTime.now().plusHours(1)
        );
        PaymentIdempotencyContext changed = new PaymentIdempotencyContext(
                context.clientId(), context.httpMethod(), context.apiPath(),
                context.idempotencyKey(), "different-hash"
        );
        when(paymentAttemptRepository
                .findByClientIdAndHttpMethodAndApiPathAndIdempotencyKey(
                        changed.clientId(), changed.httpMethod(), changed.apiPath(), changed.idempotencyKey()
                )).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.pay(request, changed))
                .isInstanceOf(PaymentAttemptConflictException.class)
                .satisfies(error -> {
                    PaymentAttemptConflictException conflict = (PaymentAttemptConflictException) error;
                    assertThat(conflict.getCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
                    assertThat(conflict.getHttpStatus().value()).isEqualTo(422);
                });
    }
}
