package com.paymentlab.voucher.payment.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.redis-idempotency")
public record RedisIdempotencyProperties(
        boolean enabled,
        String address,
        String clientId,
        Duration resultTtl,
        Duration lockWaitTime
) {
}
