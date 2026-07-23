package com.paymentlab.voucher.provider;

import com.paymentlab.voucher.provider.VoucherProviderClient.CancelVoucherRequest;
import com.paymentlab.voucher.provider.VoucherProviderClient.IssueVoucherRequest;
import com.paymentlab.voucher.provider.VoucherProviderClient.IssueVoucherResponse;
import com.paymentlab.voucher.provider.domain.ProviderVoucher;
import com.paymentlab.voucher.provider.domain.ProviderVoucherRepository;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock/voucher-provider/vouchers")
public class MockVoucherProviderController {

    private final ProviderVoucherRepository providerVoucherRepository;
    private final AtomicLong issueCallCount = new AtomicLong();
    private final AtomicLong cancelCallCount = new AtomicLong();

    public MockVoucherProviderController(ProviderVoucherRepository providerVoucherRepository) {
        this.providerVoucherRepository = providerVoucherRepository;
    }

    @PostMapping("/issue")
    public IssueVoucherResponse issue(@RequestBody IssueVoucherRequest request) {
        issueCallCount.incrementAndGet();
        String voucherNumber = "CP-" + UUID.randomUUID();
        String pinNumber = "PIN-" + UUID.randomUUID().toString().substring(0, 8);

        providerVoucherRepository.save(ProviderVoucher.issued(
                request.voucherProductCode(),
                voucherNumber,
                pinNumber,
                request.orderId()
        ));

        return new IssueVoucherResponse("00", voucherNumber, pinNumber);
    }

    @PostMapping("/cancel")
    public void cancel(@RequestBody CancelVoucherRequest request) {
        cancelCallCount.incrementAndGet();
        providerVoucherRepository.findByVoucherNumber(request.voucherNumber())
                .ifPresent(coupon -> {
                    coupon.cancel();
                    providerVoucherRepository.save(coupon);
                });
    }

    @GetMapping
    public ProviderVoucherListResponse findAll(@RequestParam(required = false) String orderId) {
        List<ProviderVoucher> vouchers = orderId == null || orderId.isBlank()
                ? providerVoucherRepository.findAllByOrderByIdDesc()
                : providerVoucherRepository.findByOrderIdOrderByIdDesc(orderId);
        return new ProviderVoucherListResponse(
                new CallCountResponse(issueCallCount.get() + cancelCallCount.get(),
                        issueCallCount.get(), cancelCallCount.get()),
                vouchers.stream().map(ProviderVoucherResponse::from).toList()
        );
    }

    public record ProviderVoucherListResponse(CallCountResponse callCount,
                                               List<ProviderVoucherResponse> vouchers) { }
    public record CallCountResponse(long total, long issue, long cancel) { }
    public record ProviderVoucherResponse(Long id, String voucherProductCode, String orderId,
                                          String voucherNumber, String pinNumber, String status,
                                          java.time.LocalDateTime createdAt) {
        private static ProviderVoucherResponse from(ProviderVoucher voucher) {
            return new ProviderVoucherResponse(voucher.getId(), voucher.getVoucherProductCode(),
                    voucher.getOrderId(), voucher.getVoucherNumber(), voucher.getPinNumber(),
                    voucher.getStatus(), voucher.getCreatedAt());
        }
    }
}
