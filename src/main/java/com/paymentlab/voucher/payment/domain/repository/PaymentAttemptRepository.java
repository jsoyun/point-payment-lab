package com.paymentlab.voucher.payment.domain.repository;

import com.paymentlab.voucher.payment.domain.PaymentAttempt;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {

    Optional<PaymentAttempt> findByOrderId(String orderId);

    Optional<PaymentAttempt> findByClientIdAndHttpMethodAndApiPathAndIdempotencyKey(
            String clientId,
            String httpMethod,
            String apiPath,
            String idempotencyKey
    );
}
