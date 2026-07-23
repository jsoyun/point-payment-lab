package com.paymentlab.voucher.payment.domain.repository;

import com.paymentlab.voucher.payment.domain.PointSourceBalance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointSourceBalanceRepository extends JpaRepository<PointSourceBalance, Long> {
}
