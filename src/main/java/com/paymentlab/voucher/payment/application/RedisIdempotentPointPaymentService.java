package com.paymentlab.voucher.payment.application;

import com.paymentlab.voucher.payment.api.PointPaymentController.PointPaymentRequest;
import com.paymentlab.voucher.payment.config.RedisIdempotencyProperties;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RedisIdempotentPointPaymentService {

    public static final String API_PATH = "/api/payments/point/redis-idempotent";
    private static final Logger log = LoggerFactory.getLogger(RedisIdempotentPointPaymentService.class);

    private final DatabasePaymentIdempotencyService databaseService;
    private final RedisPaymentResultCache resultCache;
    private final RedissonClient redissonClient;
    private final RedisIdempotencyProperties properties;
    private final PaymentRequestHasher requestHasher;

    public RedisIdempotentPointPaymentService(
            DatabasePaymentIdempotencyService databaseService,
            RedisPaymentResultCache resultCache,
            RedissonClient redissonClient,
            RedisIdempotencyProperties properties,
            PaymentRequestHasher requestHasher
    ) {
        this.databaseService = databaseService;
        this.resultCache = resultCache;
        this.redissonClient = redissonClient;
        this.properties = properties;
        this.requestHasher = requestHasher;
    }

    public RedisPaymentResult pay(String idempotencyKey, PointPaymentRequest request) {
        PaymentIdempotencyContext context = context(idempotencyKey, request);
        if (!properties.enabled()) {
            return databaseService.pay(request, context);
        }

        try {
            Optional<RedisPaymentResult> cached = resultCache.get(context);
            if (cached.isPresent()) {
                return cached.get();
            }
            return executeWithLock(request, context);
        } catch (RedisException redisFailure) {
            log.warn("Redis idempotency unavailable; falling back to MySQL: {}",
                    redisFailure.getMessage());
            return databaseService.pay(request, context);
        }
    }

    private RedisPaymentResult executeWithLock(
            PointPaymentRequest request,
            PaymentIdempotencyContext context
    ) {
        RLock lock = redissonClient.getLock(resultCache.lockKey(context));
        boolean acquired = false;
        try {
            acquired = lock.tryLock(properties.lockWaitTime().toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                Optional<RedisPaymentResult> completed = databaseService.findCompleted(context);
                if (completed.isPresent()) {
                    resultCache.put(context, completed.get());
                    return completed.get().replayedFrom("DATABASE_AFTER_LOCK_WAIT");
                }
                throw new PaymentAttemptConflictException(
                        "PAYMENT_PROCESSING",
                        "payment with this Idempotency-Key is already processing"
                );
            }

            Optional<RedisPaymentResult> cachedAfterLock = resultCache.get(context);
            if (cachedAfterLock.isPresent()) {
                return cachedAfterLock.get();
            }

            Optional<RedisPaymentResult> completed = databaseService.findCompleted(context);
            if (completed.isPresent()) {
                RedisPaymentResult replayed = completed.get().replayedFrom("DATABASE_CACHE_REBUILD");
                resultCache.put(context, replayed);
                return replayed;
            }

            RedisPaymentResult result = databaseService.pay(request, context);
            resultCache.put(context, result);
            return result;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new PaymentAttemptConflictException(
                    "PAYMENT_LOCK_INTERRUPTED",
                    "payment lock wait was interrupted"
            );
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private PaymentIdempotencyContext context(
            String idempotencyKey,
            PointPaymentRequest request
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
        if (idempotencyKey.length() > 100) {
            throw new IllegalArgumentException("Idempotency-Key must be 100 characters or fewer");
        }
        return new PaymentIdempotencyContext(
                properties.clientId(),
                "POST",
                API_PATH,
                idempotencyKey,
                requestHasher.hash(request)
        );
    }
}
