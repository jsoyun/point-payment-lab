package com.paymentlab.voucher.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "voucher_product")
public class VoucherProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "voucher_product_code", nullable = false, unique = true)
    private String voucherProductCode;

    @Column(name = "voucher_name", nullable = false)
    private String voucherName;

    @Column(name = "sell_price", nullable = false)
    private long sellPrice;

    @Column(name = "use_term", nullable = false)
    private int useTerm;

    protected VoucherProduct() {
    }

    private VoucherProduct(String voucherProductCode, String voucherName, long sellPrice, int useTerm) {
        this.voucherProductCode = voucherProductCode;
        this.voucherName = voucherName;
        this.sellPrice = sellPrice;
        this.useTerm = useTerm;
    }

    public static VoucherProduct create(String voucherProductCode, String voucherName, long sellPrice, int useTerm) {
        return new VoucherProduct(voucherProductCode, voucherName, sellPrice, useTerm);
    }

    public Long getId() {
        return id;
    }

    public String getVoucherProductCode() {
        return voucherProductCode;
    }

    public String getVoucherName() {
        return voucherName;
    }

    public long getSellPrice() {
        return sellPrice;
    }

    public int getUseTerm() {
        return useTerm;
    }
}
