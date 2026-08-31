package com.paymentlab.voucher.payment.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentlab.voucher.payment.api.PointPaymentController.PointPaymentResponse;
import com.paymentlab.voucher.payment.config.RedisIdempotencyProperties;
import java.util.Optional;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class RedisPaymentResultCache {

    private final RedissonClient redissonClient;
    private final RedisIdempotencyProperties properties;
    private final PaymentRequestHasher hasher;
    private final PaymentResponseCodec responseCodec;
    private final ObjectMapper objectMapper;

    public RedisPaymentResultCache(
            RedissonClient redissonClient,
            RedisIdempotencyProperties properties,
            PaymentRequestHasher hasher,
            PaymentResponseCodec responseCodec,
            ObjectMapper objectMapper
    ) {
        this.redissonClient = redissonClient;
        this.properties = properties;
        this.hasher = hasher;
        this.responseCodec = responseCodec;
        this.objectMapper = objectMapper;
    }

    public Optional<RedisPaymentResult> get(PaymentIdempotencyContext context) {
        String value = bucket(context).get();
        if (value == null) {
            return Optional.empty();
        }

        CachedPaymentResult cached = deserialize(value);
        if (!cached.requestHash().equals(context.requestHash())) {
            throw new PaymentAttemptConflictException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key was already used with a different payment request"
            );
        }

        PointPaymentResponse response = responseCodec.deserialize(cached.responseBody());
        return Optional.of(new RedisPaymentResult(
                response,
                cached.httpStatus(),
                true,
                "REDIS_CACHE"
        ));
    }

    public void put(PaymentIdempotencyContext context, RedisPaymentResult result) {
        CachedPaymentResult cached = new CachedPaymentResult(
                context.requestHash(),
                result.httpStatus(),
                responseCodec.serialize(result.response())
        );
        bucket(context).set(serialize(cached), properties.resultTtl());
    }

    public String lockKey(PaymentIdempotencyContext context) {
        return "idem:payment:" + scopeHash(context) + ":lock";
    }

    private RBucket<String> bucket(PaymentIdempotencyContext context) {
        String key = "idem:payment:" + scopeHash(context) + ":result";
        return redissonClient.getBucket(key, StringCodec.INSTANCE);
    }

    private String scopeHash(PaymentIdempotencyContext context) {
        return hasher.hash(context.scope());
    }

    private String serialize(CachedPaymentResult value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Redis payment result serialization failed", error);
        }
    }

    private CachedPaymentResult deserialize(String value) {
        try {
            return objectMapper.readValue(value, CachedPaymentResult.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Redis payment result deserialization failed", error);
        }
    }

    private record CachedPaymentResult(
            String requestHash,
            int httpStatus,
            String responseBody
    ) {
    }
}
