package com.paymentlab.voucher.provider;

import com.paymentlab.voucher.provider.VoucherProviderClient.IssueVoucherRequest;
import com.paymentlab.voucher.provider.VoucherProviderClient.IssueVoucherResponse;
import com.paymentlab.voucher.provider.domain.ProviderVoucher;
import com.paymentlab.voucher.provider.domain.ProviderVoucherRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class IdempotentProviderVoucherIssueService {

    private final ProviderVoucherIssueWriter providerVoucherIssueWriter;
    private final ProviderVoucherRepository providerVoucherRepository;

    public IdempotentProviderVoucherIssueService(
            ProviderVoucherIssueWriter providerVoucherIssueWriter,
            ProviderVoucherRepository providerVoucherRepository
    ) {
        this.providerVoucherIssueWriter = providerVoucherIssueWriter;
        this.providerVoucherRepository = providerVoucherRepository;
    }

    public ProviderIssueResult issue(IssueVoucherRequest request) {
        try {
            ProviderVoucher issuedVoucher = providerVoucherIssueWriter.issue(request);
            return new ProviderIssueResult(toResponse(issuedVoucher), false);
        } catch (DataIntegrityViolationException duplicateOrderId) {
            return replayExisting(request, duplicateOrderId);
        }
    }

    private ProviderIssueResult replayExisting(
            IssueVoucherRequest request,
            DataIntegrityViolationException duplicateOrderId
    ) {
        ProviderVoucher existing = providerVoucherRepository.findByOrderId(request.orderId())
                .orElseThrow(() -> duplicateOrderId);

        if (!existing.getVoucherProductCode().equals(request.voucherProductCode())) {
            throw new ProviderIssueConflictException(
                    "IDEMPOTENCY_KEY_REUSED",
                    "orderId was already used with a different voucher product"
            );
        }

        return new ProviderIssueResult(toResponse(existing), true);
    }

    private IssueVoucherResponse toResponse(ProviderVoucher voucher) {
        return new IssueVoucherResponse(
                "00",
                voucher.getVoucherNumber(),
                voucher.getPinNumber()
        );
    }

    public record ProviderIssueResult(IssueVoucherResponse response, boolean replayed) {
    }
}
