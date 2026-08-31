package com.paymentlab.voucher.payment.application;

import com.paymentlab.voucher.payment.api.PointPaymentController.PointPaymentRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class PaymentRequestHasher {

    public String hash(PointPaymentRequest request) {
        String canonical = String.join("|",
                request.orderId(),
                request.pointWalletUid(),
                String.valueOf(request.voucherProductId()),
                String.valueOf(request.pointBalanceId()),
                String.valueOf(request.point())
        );
        return hash(canonical);
    }

    public String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}
