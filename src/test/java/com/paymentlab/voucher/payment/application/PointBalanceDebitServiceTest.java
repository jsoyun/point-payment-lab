package com.paymentlab.voucher.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.paymentlab.voucher.payment.domain.PointBalance;
import com.paymentlab.voucher.payment.domain.repository.PointBalanceRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PointBalanceDebitServiceTest {

    @Mock
    private PointBalanceRepository pointBalanceRepository;

    @Mock
    private PointBalance pointBalance;

    @Test
    void returnsUpdatedBalanceWhenConditionalDebitChangesOneRow() {
        PointBalanceDebitService service = new PointBalanceDebitService(pointBalanceRepository);
        when(pointBalanceRepository.debitIfSufficient(1L, 10L, 5000)).thenReturn(1);
        when(pointBalanceRepository.findById(1L)).thenReturn(Optional.of(pointBalance));

        PointBalance result = service.debit(1L, 10L, 5000);

        assertThat(result).isSameAs(pointBalance);
        verify(pointBalanceRepository).debitIfSufficient(1L, 10L, 5000);
    }

    @Test
    void rejectsPaymentWhenAnotherTransactionUsedTheBalanceFirst() {
        PointBalanceDebitService service = new PointBalanceDebitService(pointBalanceRepository);
        when(pointBalanceRepository.debitIfSufficient(1L, 10L, 5000)).thenReturn(0);

        assertThatThrownBy(() -> service.debit(1L, 10L, 5000))
                .isInstanceOf(PointBalanceConflictException.class)
                .satisfies(error -> assertThat(
                        ((PointBalanceConflictException) error).getCode()
                ).isEqualTo("POINT_BALANCE_CONFLICT"));
    }
}
