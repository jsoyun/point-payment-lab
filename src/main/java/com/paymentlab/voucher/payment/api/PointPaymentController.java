package com.paymentlab.voucher.payment.api;

import com.paymentlab.voucher.payment.application.LegacyPointPaymentService;
import com.paymentlab.voucher.payment.application.IdempotentPointPaymentService;
import com.paymentlab.voucher.payment.application.IdempotentPointPaymentService.IdempotentPaymentResult;
import com.paymentlab.voucher.payment.application.RedisIdempotentPointPaymentService;
import com.paymentlab.voucher.payment.application.RedisPaymentResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


/**
 * 포인트 결제 API 컨트롤러
 */
@RestController
@RequestMapping("/api/payments/point")
public class PointPaymentController {

    private final LegacyPointPaymentService legacyPointPaymentService;
    private final IdempotentPointPaymentService idempotentPointPaymentService;
    private final RedisIdempotentPointPaymentService redisIdempotentPointPaymentService;

    public PointPaymentController(
            LegacyPointPaymentService legacyPointPaymentService,
            IdempotentPointPaymentService idempotentPointPaymentService,
            RedisIdempotentPointPaymentService redisIdempotentPointPaymentService
    ) {
        this.legacyPointPaymentService = legacyPointPaymentService;
        this.idempotentPointPaymentService = idempotentPointPaymentService;
        this.redisIdempotentPointPaymentService = redisIdempotentPointPaymentService;
    }

    @PostMapping("/legacy")
    @ResponseStatus(HttpStatus.CREATED)
    public PointPaymentResponse payLegacy(@Valid @RequestBody PointPaymentRequest request) {
        return legacyPointPaymentService.pay(request);
    }

    @PostMapping("/idempotent")
    public ResponseEntity<PointPaymentResponse> payIdempotent(
            @Valid @RequestBody PointPaymentRequest request
    ) {
        IdempotentPaymentResult result = idempotentPointPaymentService.pay(request);
        return ResponseEntity
                .status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("Idempotency-Replayed", String.valueOf(result.replayed()))
                .body(result.response());
    }

    @PostMapping("/redis-idempotent")
    public ResponseEntity<PointPaymentResponse> payRedisIdempotent(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PointPaymentRequest request
    ) {
        RedisPaymentResult result = redisIdempotentPointPaymentService.pay(idempotencyKey, request);
        return ResponseEntity
                .status(result.httpStatus())
                .header("Idempotency-Replayed", String.valueOf(result.replayed()))
                .header("Idempotency-Source", result.source())
                .body(result.response());
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
