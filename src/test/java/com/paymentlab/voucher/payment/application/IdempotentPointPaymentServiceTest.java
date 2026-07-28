package com.paymentlab.voucher.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.paymentlab.voucher.payment.api.PointPaymentController.PointPaymentRequest;
import com.paymentlab.voucher.payment.api.PointPaymentController.PointPaymentResponse;
import com.paymentlab.voucher.payment.domain.PaymentAttempt;
import com.paymentlab.voucher.payment.domain.repository.PaymentAttemptRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class IdempotentPointPaymentServiceTest {

    @Mock
    private LegacyPointPaymentService legacyPointPaymentService;

    @Mock
    private PaymentAttemptWriter paymentAttemptWriter;

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    @InjectMocks
    private IdempotentPointPaymentService service;

    private final PointPaymentRequest request = new PointPaymentRequest(
            "ORDER-IDEMPOTENT-001",
            "point-wallet-001",
            1L,
            1L,
            5000
    );

    private final PointPaymentResponse response = new PointPaymentResponse(
            "A voucher has been issued successfully",
            "ORDER-IDEMPOTENT-001",
            "CP-001",
            "PIN-001",
            5000,
            "5000"
    );

    @Test
    void firstRequestClaimsOrderAndProcessesPayment() {
        when(legacyPointPaymentService.pay(request)).thenReturn(response);

        IdempotentPointPaymentService.IdempotentPaymentResult result = service.pay(request);

        assertThat(result.replayed()).isFalse();
        assertThat(result.response()).isEqualTo(response);
        verify(paymentAttemptWriter).claim(request);
        verify(paymentAttemptWriter).markSucceeded(request.orderId(), response);
    }

    @Test
    void completedDuplicateReturnsStoredResultWithoutCallingProviderFlowAgain() {
        PaymentAttempt completed = PaymentAttempt.processing(request);
        completed.succeed(response);
        duplicateClaim();
        when(paymentAttemptRepository.findByOrderId(request.orderId()))
                .thenReturn(Optional.of(completed));

        IdempotentPointPaymentService.IdempotentPaymentResult result = service.pay(request);

        assertThat(result.replayed()).isTrue();
        assertThat(result.response().voucherNumber()).isEqualTo("CP-001");
        verify(legacyPointPaymentService, never()).pay(request);
    }

    @Test
    void concurrentDuplicateIsRejectedWhileFirstRequestIsProcessing() {
        PaymentAttempt processing = PaymentAttempt.processing(request);
        duplicateClaim();
        when(paymentAttemptRepository.findByOrderId(request.orderId()))
                .thenReturn(Optional.of(processing));

        assertThatThrownBy(() -> service.pay(request))
                .isInstanceOf(PaymentAttemptConflictException.class)
                .extracting(error -> ((PaymentAttemptConflictException) error).getCode())
                .isEqualTo("PAYMENT_PROCESSING");
        verify(legacyPointPaymentService, never()).pay(request);
    }

    @Test
    void sameOrderIdWithDifferentPayloadIsRejected() {
        PointPaymentRequest changed = new PointPaymentRequest(
                request.orderId(),
                request.pointWalletUid(),
                request.voucherProductId(),
                request.pointBalanceId(),
                3000
        );
        PaymentAttempt existing = PaymentAttempt.processing(request);
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(paymentAttemptWriter).claim(changed);
        when(paymentAttemptRepository.findByOrderId(request.orderId()))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.pay(changed))
                .isInstanceOf(PaymentAttemptConflictException.class)
                .extracting(error -> ((PaymentAttemptConflictException) error).getCode())
                .isEqualTo("IDEMPOTENCY_KEY_REUSED");
        verify(legacyPointPaymentService, never()).pay(changed);
    }

    @Test
    void paymentFailureIsRecordedAndRethrown() {
        IllegalStateException failure = new IllegalStateException("payment failed");
        when(legacyPointPaymentService.pay(request)).thenThrow(failure);

        assertThatThrownBy(() -> service.pay(request)).isSameAs(failure);
        verify(paymentAttemptWriter).markFailed(request.orderId(), failure);
    }

    private void duplicateClaim() {
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(paymentAttemptWriter).claim(request);
    }
}
