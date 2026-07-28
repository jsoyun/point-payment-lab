package com.paymentlab.voucher.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.paymentlab.voucher.provider.VoucherProviderClient.IssueVoucherRequest;
import com.paymentlab.voucher.provider.domain.ProviderVoucher;
import com.paymentlab.voucher.provider.domain.ProviderVoucherRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class IdempotentProviderVoucherIssueServiceTest {

    @Mock
    private ProviderVoucherIssueWriter providerVoucherIssueWriter;

    @Mock
    private ProviderVoucherRepository providerVoucherRepository;

    @InjectMocks
    private IdempotentProviderVoucherIssueService service;

    private final IssueVoucherRequest request =
            new IssueVoucherRequest("VOUCHER-COFFEE-5000", "PROVIDER-ORDER-001");

    @Test
    void firstRequestReturnsNewVoucher() {
        ProviderVoucher voucher = voucher(request);
        when(providerVoucherIssueWriter.issue(request)).thenReturn(voucher);

        IdempotentProviderVoucherIssueService.ProviderIssueResult result =
                service.issue(request);

        assertThat(result.replayed()).isFalse();
        assertThat(result.response().voucherNumber()).isEqualTo("CP-001");
        assertThat(result.response().pinNumber()).isEqualTo("PIN-001");
    }

    @Test
    void duplicateRequestReturnsExistingVoucher() {
        ProviderVoucher voucher = voucher(request);
        duplicateInsert(request);
        when(providerVoucherRepository.findByOrderId(request.orderId()))
                .thenReturn(Optional.of(voucher));

        IdempotentProviderVoucherIssueService.ProviderIssueResult result =
                service.issue(request);

        assertThat(result.replayed()).isTrue();
        assertThat(result.response().voucherNumber()).isEqualTo("CP-001");
        assertThat(result.response().pinNumber()).isEqualTo("PIN-001");
    }

    @Test
    void sameOrderIdWithDifferentProductIsRejected() {
        IssueVoucherRequest changed =
                new IssueVoucherRequest("VOUCHER-CAKE-10000", request.orderId());
        duplicateInsert(changed);
        when(providerVoucherRepository.findByOrderId(request.orderId()))
                .thenReturn(Optional.of(voucher(request)));

        assertThatThrownBy(() -> service.issue(changed))
                .isInstanceOf(ProviderIssueConflictException.class)
                .extracting(error -> ((ProviderIssueConflictException) error).getCode())
                .isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void unrelatedConstraintFailureIsRethrownWhenOrderDoesNotExist() {
        duplicateInsert(request);
        when(providerVoucherRepository.findByOrderId(request.orderId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issue(request))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private ProviderVoucher voucher(IssueVoucherRequest issueRequest) {
        return ProviderVoucher.issued(
                issueRequest.voucherProductCode(),
                "CP-001",
                "PIN-001",
                issueRequest.orderId()
        );
    }

    private void duplicateInsert(IssueVoucherRequest issueRequest) {
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(providerVoucherIssueWriter).issue(issueRequest);
    }
}
