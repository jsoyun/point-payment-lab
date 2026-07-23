package com.paymentlab.voucher.payment.api;

import com.paymentlab.voucher.payment.application.LegacyPointPaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


/**
 * 포인트 결제 API 컨트롤러
 */
@RestController
@RequestMapping("/api/payments/point")
public class PointPaymentController {

    private final LegacyPointPaymentService legacyPointPaymentService;

    public PointPaymentController(LegacyPointPaymentService legacyPointPaymentService) {
        this.legacyPointPaymentService = legacyPointPaymentService;
    }

    @PostMapping("/legacy")
    @ResponseStatus(HttpStatus.CREATED)
    public PointPaymentResponse payLegacy(@Valid @RequestBody PointPaymentRequest request) {
        return legacyPointPaymentService.pay(request);
    }

    public record PointPaymentRequest(
            @NotBlank String orderId,
            @NotBlank String pointWalletUid,
            @NotNull Long voucherProductId,
            @NotNull Long pointBalanceId,
            @Min(1) long point
    ) {
    }

    public record PointPaymentResponse(
            String message,
            String orderId,
            String voucherNumber,
            String pinNumber,
            long pointAmount,
            String balanceAfterPayment
    ) {
    }
}
