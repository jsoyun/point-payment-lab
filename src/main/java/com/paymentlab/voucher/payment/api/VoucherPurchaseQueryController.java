package com.paymentlab.voucher.payment.api;

import com.paymentlab.voucher.payment.domain.VoucherPurchase;
import com.paymentlab.voucher.payment.domain.repository.VoucherPurchaseRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/voucher-purchases")
public class VoucherPurchaseQueryController {

    private final VoucherPurchaseRepository repository;

    public VoucherPurchaseQueryController(VoucherPurchaseRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<VoucherPurchaseResponse> findAll(@RequestParam(required = false) String orderId) {
        List<VoucherPurchase> purchases = orderId == null || orderId.isBlank()
                ? repository.findAllByOrderByIdDesc()
                : repository.findByOrderIdOrderByIdDesc(orderId);
        return purchases.stream().map(VoucherPurchaseResponse::from).toList();
    }

    public record VoucherPurchaseResponse(Long id, String orderId, Long voucherProductId,
                                          String voucherNumber, String pinNumber, long pointAmount,
                                          String paymentMethod, String issueStatus, String useStatus,
                                          LocalDateTime validFrom, LocalDateTime validUntil,
                                          LocalDateTime usedOrCanceledAt) {
        private static VoucherPurchaseResponse from(VoucherPurchase purchase) {
            return new VoucherPurchaseResponse(purchase.getId(), purchase.getOrderId(),
                    purchase.getVoucherProductId(), purchase.getVoucherNumber(), purchase.getPinNumber(),
                    purchase.getPointAmount(), purchase.getPaymentMethod(), purchase.getIssueStatus(),
                    purchase.getUseStatus(), purchase.getValidFrom(), purchase.getValidUntil(),
                    purchase.getUsedOrCanceledAt());
        }
    }
}
