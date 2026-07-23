package com.paymentlab.voucher.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "point_ledger")
// 포인트 사용/환불 원장. 잔액 변동 이력을 기록
public class PointLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "point_wallet_id", nullable = false)
    private Long pointWalletId;

    @Column(name = "point_balance_id", nullable = false)
    private Long pointBalanceId;

    @Column(nullable = false)
    private String state;

    @Column(nullable = false)
    private String amount;

    @Column(nullable = false)
    private String balance;

    @Column(nullable = false)
    private String title;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    protected PointLedger() {
    }

    private PointLedger(Long pointWalletId, Long pointBalanceId, String state, long amount, String balance, String title) {
        this.pointWalletId = pointWalletId;
        this.pointBalanceId = pointBalanceId;
        this.state = state;
        this.amount = String.valueOf(amount);
        this.balance = balance;
        this.title = title;
        this.occurredAt = LocalDateTime.now();
    }

    public static PointLedger withdrawal(
            Long pointWalletId,
            Long pointBalanceId,
            long amount,
            String balance,
            String title
    ) {
        return new PointLedger(pointWalletId, pointBalanceId, "WITHDRAWAL", amount, balance, title);
    }

    public static PointLedger returned(
            Long pointWalletId,
            Long pointBalanceId,
            long amount,
            String balance,
            String title
    ) {
        return new PointLedger(pointWalletId, pointBalanceId, "RETURN", amount, balance, title);
    }

    public Long getId() {
        return id;
    }

    public Long getPointWalletId() {
        return pointWalletId;
    }

    public Long getPointBalanceId() {
        return pointBalanceId;
    }
}
