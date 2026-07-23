package com.paymentlab.voucher.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "voucher_purchase")
// 바우처 구매/결제 결과 저장
public class VoucherPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "voucher_number", nullable = false, unique = true)
    private String voucherNumber;

    @Column(name = "pin_number", nullable = false)
    private String pinNumber;

    @Column(name = "order_id", nullable = false, unique = true)
    private String orderId;

    @Column(name = "voucher_product_id", nullable = false)
    private Long voucherProductId;

    @Column(name = "point_ledger_id", nullable = false, unique = true)
    private Long pointLedgerId;

    @Column(name = "payment_type", nullable = false)
    private String paymentType;

    @Column(name = "point_amount", nullable = false)
    private long pointAmount;

    @Column(name = "card_amount", nullable = false)
    private long cardAmount;

    @Column(name = "payment_method", nullable = false)
    private String paymentMethod;

    @Column(name = "issue_status", nullable = false)
    private String issueStatus;

    @Column(name = "use_status", nullable = false)
    private String useStatus;

    @Column(name = "valid_from", nullable = false)
    private LocalDateTime validFrom;

    @Column(name = "valid_until", nullable = false)
    private LocalDateTime validUntil;

    @Column(name = "used_or_canceled_at")
    private LocalDateTime usedOrCanceledAt;

    protected VoucherPurchase() {
    }

    private VoucherPurchase(
            String voucherNumber,
            String pinNumber,
            String orderId,
            Long voucherProductId,
            Long pointLedgerId,
            long pointAmount,
            LocalDateTime validFrom,
            LocalDateTime validUntil
    ) {
        this.voucherNumber = voucherNumber;
        this.pinNumber = pinNumber;
        this.orderId = orderId;
        this.voucherProductId = voucherProductId;
        this.pointLedgerId = pointLedgerId;
        this.paymentType = "POINT";
        this.pointAmount = pointAmount;
        this.cardAmount = 0;
        this.paymentMethod = "전액 포인트";
        this.issueStatus = "ISSUED";
        this.useStatus = "UNUSED";
        this.validFrom = validFrom;
        this.validUntil = validUntil;
    }

    public static VoucherPurchase pointPayment(
            String voucherNumber,
            String pinNumber,
            String orderId,
            Long voucherProductId,
            Long pointLedgerId,
            long pointAmount,
            LocalDateTime validFrom,
            LocalDateTime validUntil
    ) {
        return new VoucherPurchase(
                voucherNumber,
                pinNumber,
                orderId,
                voucherProductId,
                pointLedgerId,
                pointAmount,
                validFrom,
                validUntil
        );
    }

    public String getVoucherNumber() {
        return voucherNumber;
    }

    public Long getId() { return id; }
    public String getPinNumber() { return pinNumber; }
    public String getOrderId() { return orderId; }
    public long getPointAmount() { return pointAmount; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getIssueStatus() { return issueStatus; }
    public String getUseStatus() { return useStatus; }
    public LocalDateTime getValidFrom() { return validFrom; }
    public LocalDateTime getValidUntil() { return validUntil; }
    public LocalDateTime getUsedOrCanceledAt() { return usedOrCanceledAt; }

    public Long getVoucherProductId() {
        return voucherProductId;
    }

    public Long getPointLedgerId() {
        return pointLedgerId;
    }

    public long getPointUsed() {
        return pointAmount;
    }

    public void cancel() {
        this.issueStatus = "CANCELED";
        this.useStatus = "CANCELED";
        this.usedOrCanceledAt = LocalDateTime.now();
    }
}
