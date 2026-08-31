package com.paymentlab.voucher.payment.domain.repository;

import com.paymentlab.voucher.payment.domain.PointBalance;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointBalanceRepository extends JpaRepository<PointBalance, Long> {
    List<PointBalance> findByPointWalletIdOrderByIdAsc(Long pointWalletId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update point_balance
               set balance = cast(balance as signed) - :amount
             where id = :pointBalanceId
               and point_wallet_id = :pointWalletId
               and cast(balance as signed) >= :amount
            """, nativeQuery = true)
    int debitIfSufficient(
            @Param("pointBalanceId") Long pointBalanceId,
            @Param("pointWalletId") Long pointWalletId,
            @Param("amount") long amount
    );
}
