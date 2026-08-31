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
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisConnectionException;

@ExtendWith(MockitoExtension.class)
class RedisIdempotentPointPaymentServiceTest {

    @Mock
    private DatabasePaymentIdempotencyService databaseService;
    @Mock
    private RedisPaymentResultCache resultCache;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private PaymentRequestHasher requestHasher;
    @Mock
    private RLock lock;

    private RedisIdempotentPointPaymentService service;

    private final PointPaymentRequest request = new PointPaymentRequest(
            "ORDER-REDIS-001", "point-wallet-001", 1L, 1L, 5000
    );
    private final PointPaymentResponse response = new PointPaymentResponse(
            "A voucher has been issued successfully",
            "ORDER-REDIS-001", "CP-001", "PIN-001", 5000, "5000"
    );

    @BeforeEach
    void setUp() {
        RedisIdempotencyProperties properties = new RedisIdempotencyProperties(
                true,
                "redis://127.0.0.1:6379",
                "point-payment-lab-mall",
                Duration.ofHours(1),
                Duration.ofMillis(500)
        );
        service = new RedisIdempotentPointPaymentService(
                databaseService, resultCache, redissonClient, properties, requestHasher
        );
        when(requestHasher.hash(request)).thenReturn("request-hash");
    }

    @Test
    void cacheHitReturnsOriginalResultWithoutDatabasePayment() {
        RedisPaymentResult cached = new RedisPaymentResult(response, 201, true, "REDIS_CACHE");
        when(resultCache.get(any())).thenReturn(Optional.of(cached));

        RedisPaymentResult result = service.pay("KEY-001", request);

        assertThat(result).isEqualTo(cached);
        verify(databaseService, never()).pay(any(), any());
    }

    @Test
    void cacheMissUsesDistributedLockAndStoresResult() throws InterruptedException {
        RedisPaymentResult created = new RedisPaymentResult(response, 201, false, "DATABASE_CREATED");
        when(resultCache.get(any())).thenReturn(Optional.empty());
        when(resultCache.lockKey(any())).thenReturn("idem:payment:hash:lock");
        when(redissonClient.getLock("idem:payment:hash:lock")).thenReturn(lock);
        when(lock.tryLock(500, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(databaseService.findCompleted(any())).thenReturn(Optional.empty());
        when(databaseService.pay(any(), any())).thenReturn(created);

        RedisPaymentResult result = service.pay("KEY-001", request);

        assertThat(result).isEqualTo(created);
        verify(resultCache).put(any(), any());
        verify(lock).unlock();
    }

    @Test
    void redisFailureFallsBackToDatabaseIdempotency() {
        RedisPaymentResult database = new RedisPaymentResult(
                response, 201, true, "DATABASE_REPLAY"
        );
        when(resultCache.get(any())).thenThrow(new RedisConnectionException("redis down"));
        when(databaseService.pay(any(), any())).thenReturn(database);

        RedisPaymentResult result = service.pay("KEY-001", request);

        assertThat(result).isEqualTo(database);
        verify(databaseService).pay(any(), any());
    }

    @Test
    void busyLockReturnsProcessingWhenNoCompletedDatabaseResultExists() throws InterruptedException {
        when(resultCache.get(any())).thenReturn(Optional.empty());
        when(resultCache.lockKey(any())).thenReturn("idem:payment:hash:lock");
        when(redissonClient.getLock("idem:payment:hash:lock")).thenReturn(lock);
        when(lock.tryLock(500, TimeUnit.MILLISECONDS)).thenReturn(false);
        when(databaseService.findCompleted(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pay("KEY-001", request))
                .isInstanceOf(PaymentAttemptConflictException.class)
                .extracting(error -> ((PaymentAttemptConflictException) error).getCode())
                .isEqualTo("PAYMENT_PROCESSING");
    }
}
