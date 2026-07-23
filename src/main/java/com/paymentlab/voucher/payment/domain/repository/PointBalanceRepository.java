package com.paymentlab.voucher.payment.domain.repository;

import com.paymentlab.voucher.payment.domain.PointBalance;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointBalanceRepository extends JpaRepository<PointBalance, Long> {
    List<PointBalance> findByPointWalletIdOrderByIdAsc(Long pointWalletId);
}
