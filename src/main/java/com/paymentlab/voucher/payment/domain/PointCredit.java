package com.paymentlab.voucher.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "point_credit")
// 포인트 지급 또는 환불성 입금 기록
public class PointCredit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String type;

    @Column(name = "request_uid", nullable = false, unique = true)
    private String requestUid;

    @Column(nullable = false)
    private String value;

    @Column(name = "point_ledger_id", nullable = false, unique = true)
    private Long pointLedgerId;

    protected PointCredit() {
    }

    private PointCredit(String type, String requestUid, String value, Long pointLedgerId) {
        this.type = type;
        this.requestUid = requestUid;
        this.value = value;
        this.pointLedgerId = pointLedgerId;
    }

    public static PointCredit returned(String voucherNumber, long point, Long pointLedgerId) {
        return new PointCredit("return", voucherNumber, String.valueOf(point), pointLedgerId);
    }
}
