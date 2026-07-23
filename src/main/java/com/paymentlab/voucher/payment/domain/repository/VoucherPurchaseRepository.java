package com.paymentlab.voucher.payment.domain.repository;

import com.paymentlab.voucher.payment.domain.VoucherPurchase;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoucherPurchaseRepository extends JpaRepository<VoucherPurchase, Long> {

    Optional<VoucherPurchase> findByVoucherNumber(String voucherNumber);

    List<VoucherPurchase> findAllByOrderByIdDesc();

    List<VoucherPurchase> findByOrderIdOrderByIdDesc(String orderId);
}
