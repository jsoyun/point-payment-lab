package com.paymentlab.voucher.payment.application;

import com.paymentlab.voucher.payment.domain.PointBalance;
import com.paymentlab.voucher.payment.domain.repository.PointBalanceRepository;
import org.springframework.stereotype.Service;

@Service
public class PointBalanceDebitService {

    private final PointBalanceRepository pointBalanceRepository;

    public PointBalanceDebitService(PointBalanceRepository pointBalanceRepository) {
        this.pointBalanceRepository = pointBalanceRepository;
    }

    public PointBalance debit(Long pointBalanceId, Long pointWalletId, long amount) {
        int affectedRows = pointBalanceRepository.debitIfSufficient(
                pointBalanceId, pointWalletId, amount
        );
        if (affectedRows != 1) {
            throw new PointBalanceConflictException(
                    "POINT_BALANCE_CONFLICT",
                    "point balance was already used by another payment or is insufficient"
            );
        }
        return pointBalanceRepository.findById(pointBalanceId)
                .orElseThrow(() -> new IllegalStateException("point balance not found after debit"));
    }
}
