package com.paymentlab.voucher.payment.domain;

import com.paymentlab.voucher.common.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "point_balance")
// 지갑 기준 총 포인트 잔액. 결제 시 차감되고 환불 시 복구됨
public class PointBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "point_wallet_id", nullable = false)
    private Long pointWalletId;

    @Column(nullable = false)
    private String balance;

    protected PointBalance() {
    }

    public Long getId() {
        return id;
    }

    public Long getPointWalletId() {
        return pointWalletId;
    }

    public String getBalance() {
        return balance;
    }

    public void subtract(long point) {
        this.balance = Money.subtract(this.balance, point);
    }

    public void add(long point) {
        this.balance = Money.add(this.balance, point);
    }
}
