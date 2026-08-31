package com.paymentlab.voucher.payment.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RedisIdempotencyProperties.class)
public class RedisIdempotencyConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisIdempotencyProperties properties) {
        Config config = new Config();
        // Redis가 내려가 있어도 애플리케이션은 기동하고 첫 사용 시 DB fallback을 수행한다.
        config.setLazyInitialization(true);
        config.useSingleServer()
                .setAddress(properties.address())
                .setConnectTimeout(1_000)
                .setTimeout(1_000)
                .setRetryAttempts(1)
                .setRetryInterval(200)
                .setConnectionMinimumIdleSize(1)
                .setConnectionPoolSize(8);
        return Redisson.create(config);
    }
}
