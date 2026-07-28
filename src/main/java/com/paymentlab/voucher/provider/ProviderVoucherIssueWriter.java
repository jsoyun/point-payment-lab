package com.paymentlab.voucher.provider;

import com.paymentlab.voucher.provider.VoucherProviderClient.IssueVoucherRequest;
import com.paymentlab.voucher.provider.domain.ProviderVoucher;
import com.paymentlab.voucher.provider.domain.ProviderVoucherRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ProviderVoucherIssueWriter {

    private final ProviderVoucherRepository providerVoucherRepository;

    public ProviderVoucherIssueWriter(ProviderVoucherRepository providerVoucherRepository) {
        this.providerVoucherRepository = providerVoucherRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProviderVoucher issue(IssueVoucherRequest request) {
        String voucherNumber = "CP-" + UUID.randomUUID();
        String pinNumber = "PIN-" + UUID.randomUUID().toString().substring(0, 8);

        return providerVoucherRepository.saveAndFlush(ProviderVoucher.issued(
                request.voucherProductCode(),
                voucherNumber,
                pinNumber,
                request.orderId()
        ));
    }
}
