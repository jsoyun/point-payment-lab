package com.paymentlab.voucher.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "point_wallet")
// 사용자 포인트 지갑. 결제 요청의 pointWalletUid로 조회되는 기준 테이블
public class PointWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "point_wallet_uid", nullable = false, unique = true)
    private String pointWalletUid;

    protected PointWallet() {
    }

    public Long getId() {
        return id;
    }

    public String getPointWalletUid() {
        return pointWalletUid;
    }
}
