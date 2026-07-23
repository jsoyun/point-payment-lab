package com.paymentlab.voucher.provider.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderVoucherRepository extends JpaRepository<ProviderVoucher, Long> {

    Optional<ProviderVoucher> findByVoucherNumber(String voucherNumber);

    List<ProviderVoucher> findAllByOrderByIdDesc();

    List<ProviderVoucher> findByOrderIdOrderByIdDesc(String orderId);
}
