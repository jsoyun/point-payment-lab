package com.paymentlab.voucher.payment.domain.repository;

import com.paymentlab.voucher.payment.domain.VoucherProduct;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoucherProductRepository extends JpaRepository<VoucherProduct, Long> {

    Optional<VoucherProduct> findByVoucherProductCode(String voucherProductCode);
}
