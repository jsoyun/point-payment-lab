package com.paymentlab.voucher.payment.domain.repository;

import com.paymentlab.voucher.payment.domain.PointLot;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointLotRepository extends JpaRepository<PointLot, Long> {

    @Query("""
            select u
            from PointLot u
            where u.pointWalletId = :pointWalletId
              and u.pointBalanceId = :pointBalanceId
              and u.status is null
              and u.voucherNumber is null
              and u.expiresAt > current_timestamp
            order by u.expiresAt asc
            """)
    List<PointLot> findUsableLots(
            @Param("pointWalletId") Long pointWalletId,
            @Param("pointBalanceId") Long pointBalanceId
    );

    List<PointLot> findByVoucherNumber(String voucherNumber);
}
