package com.paymentlab.voucher.payment.domain;

import com.paymentlab.voucher.payment.api.PointPaymentController.PointPaymentRequest;
import com.paymentlab.voucher.payment.api.PointPaymentController.PointPaymentResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "payment_attempt")
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private String orderId;

    @Column(name = "point_wallet_uid", nullable = false)
    private String pointWalletUid;

    @Column(name = "voucher_product_id", nullable = false)
    private Long voucherProductId;

    @Column(name = "point_balance_id", nullable = false)
    private Long pointBalanceId;

    @Column(name = "requested_point", nullable = false)
    private long requestedPoint;

    @Column(nullable = false)
    private String status;

    @Column(name = "voucher_number")
    private String voucherNumber;

    @Column(name = "pin_number")
    private String pinNumber;

    @Column(name = "point_amount")
    private Long pointAmount;

    @Column(name = "balance_after_payment")
    private String balanceAfterPayment;

    @Column(name = "failure_message")
    private String failureMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected PaymentAttempt() {
    }

    private PaymentAttempt(PointPaymentRequest request) {
        this.orderId = request.orderId();
        this.pointWalletUid = request.pointWalletUid();
        this.voucherProductId = request.voucherProductId();
        this.pointBalanceId = request.pointBalanceId();
        this.requestedPoint = request.point();
        this.status = "PROCESSING";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static PaymentAttempt processing(PointPaymentRequest request) {
        return new PaymentAttempt(request);
    }

    public boolean matches(PointPaymentRequest request) {
        return Objects.equals(orderId, request.orderId())
                && Objects.equals(pointWalletUid, request.pointWalletUid())
                && Objects.equals(voucherProductId, request.voucherProductId())
                && Objects.equals(pointBalanceId, request.pointBalanceId())
                && requestedPoint == request.point();
    }

    public void succeed(PointPaymentResponse response) {
        this.status = "SUCCEEDED";
        this.voucherNumber = response.voucherNumber();
        this.pinNumber = response.pinNumber();
        this.pointAmount = response.pointAmount();
        this.balanceAfterPayment = response.balanceAfterPayment();
        this.failureMessage = null;
        this.updatedAt = LocalDateTime.now();
    }

    public void fail(String message) {
        this.status = "FAILED";
        this.failureMessage = abbreviate(message);
        this.updatedAt = LocalDateTime.now();
    }

    public PointPaymentResponse toResponse() {
        if (!isSucceeded()) {
            throw new IllegalStateException("payment attempt has no successful result");
        }
        return new PointPaymentResponse(
                "Existing successful payment result returned",
                orderId,
                voucherNumber,
                pinNumber,
                pointAmount,
                balanceAfterPayment
        );
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "unknown payment failure";
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    public String getOrderId() {
        return orderId;
    }

    public String getStatus() {
        return status;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public boolean isProcessing() {
        return "PROCESSING".equals(status);
    }

    public boolean isSucceeded() {
        return "SUCCEEDED".equals(status);
    }

    public boolean isFailed() {
        return "FAILED".equals(status);
    }
}
