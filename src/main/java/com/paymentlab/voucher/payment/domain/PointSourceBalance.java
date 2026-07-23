package com.paymentlab.voucher.payment.domain;

import com.paymentlab.voucher.common.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "point_source_balance")
// 포인트 출처별 잔액. 총 잔액의 하위 단위
public class PointSourceBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "point_wallet_id", nullable = false)
    private Long pointWalletId;

    @Column(name = "point_balance_id", nullable = false)
    private Long pointBalanceId;

    @Column(nullable = false)
    private String balance;

    protected PointSourceBalance() {
    }

    public void subtract(long point) {
        this.balance = Money.subtract(this.balance, point);
    }

    public void add(long point) {
        this.balance = Money.add(this.balance, point);
    }
}
