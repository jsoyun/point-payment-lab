package com.paymentlab.voucher.refund.api;

import com.paymentlab.voucher.refund.application.LegacyPointRefundService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/refunds/point")
public class PointRefundController {

    private final LegacyPointRefundService legacyPointRefundService;

    public PointRefundController(LegacyPointRefundService legacyPointRefundService) {
        this.legacyPointRefundService = legacyPointRefundService;
    }

    @PostMapping("/legacy")
    public PointRefundResponse refundLegacy(@Valid @RequestBody PointRefundRequest request) {
        return legacyPointRefundService.refund(request);
    }

    public record PointRefundRequest(@NotBlank String voucherNumber) {
    }

    public record PointRefundResponse(String message, String voucherNumber, long refundedPoint) {
    }
}
