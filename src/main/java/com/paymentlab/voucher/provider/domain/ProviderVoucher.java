package com.paymentlab.voucher.provider.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "provider_voucher")
// 외부 바우처 제공사 API를 흉내 내는 mock 저장소
public class ProviderVoucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "voucher_product_code", nullable = false)
    private String voucherProductCode;

    @Column(name = "voucher_number", nullable = false, unique = true)
    private String voucherNumber;

    @Column(name = "pin_number", nullable = false)
    private String pinNumber;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ProviderVoucher() {
    }

    private ProviderVoucher(String voucherProductCode, String voucherNumber, String pinNumber, String orderId) {
        this.voucherProductCode = voucherProductCode;
        this.voucherNumber = voucherNumber;
        this.pinNumber = pinNumber;
        this.orderId = orderId;
        this.status = "ISSUED";
        this.createdAt = LocalDateTime.now();
    }

    public static ProviderVoucher issued(String voucherProductCode, String voucherNumber, String pinNumber, String orderId) {
        return new ProviderVoucher(voucherProductCode, voucherNumber, pinNumber, orderId);
    }

    public void cancel() {
        this.status = "CANCELED";
    }

    public Long getId() { return id; }
    public String getVoucherProductCode() { return voucherProductCode; }
    public String getVoucherNumber() { return voucherNumber; }
    public String getPinNumber() { return pinNumber; }
    public String getOrderId() { return orderId; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
