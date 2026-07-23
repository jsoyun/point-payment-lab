package com.paymentlab.voucher.payment.domain.repository;

import com.paymentlab.voucher.payment.domain.PointWallet;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointWalletRepository extends JpaRepository<PointWallet, Long> {

    Optional<PointWallet> findByPointWalletUid(String pointWalletUid);
}
