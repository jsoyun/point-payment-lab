package com.paymentlab.voucher.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "point_lot")
// 만료일과 출처를 가진 포인트 묶음. 결제 시 만료일 빠른 순으로 사용됨
public class PointLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "point_wallet_id", nullable = false)
    private Long pointWalletId;

    @Column(name = "point_source_balance_id", nullable = false)
    private Long pointSourceBalanceId;

    @Column(name = "point_balance_id", nullable = false)
    private Long pointBalanceId;

    @Column(nullable = false)
    private String amount;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "voucher_number")
    private String voucherNumber;

    private String status;

    protected PointLot() {
    }

    private PointLot(Long pointWalletId, Long pointSourceBalanceId, Long pointBalanceId, String amount, LocalDateTime expiresAt) {
        this.pointWalletId = pointWalletId;
        this.pointSourceBalanceId = pointSourceBalanceId;
        this.pointBalanceId = pointBalanceId;
        this.amount = amount;
        this.expiresAt = expiresAt;
    }

    public static PointLot restOf(PointLot source, long amount) {
        return new PointLot(
                source.pointWalletId,
                source.pointSourceBalanceId,
                source.pointBalanceId,
                String.valueOf(amount),
                source.expiresAt
        );
    }

    public Long getPointSourceBalanceId() {
        return pointSourceBalanceId;
    }

    public String getAmount() {
        return amount;
    }

    public void markUsed(String usedAmount, String voucherNumber) {
        this.amount = usedAmount;
        this.voucherNumber = voucherNumber;
        this.status = "USED";
    }

    public void restore() {
        this.voucherNumber = null;
        this.status = null;
    }
}
