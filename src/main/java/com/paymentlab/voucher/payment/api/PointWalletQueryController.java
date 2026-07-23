package com.paymentlab.voucher.payment.api;

import com.paymentlab.voucher.payment.domain.PointBalance;
import com.paymentlab.voucher.payment.domain.PointWallet;
import com.paymentlab.voucher.payment.domain.repository.PointBalanceRepository;
import com.paymentlab.voucher.payment.domain.repository.PointWalletRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/point-wallets")
public class PointWalletQueryController {

    private final PointWalletRepository pointWalletRepository;
    private final PointBalanceRepository pointBalanceRepository;

    public PointWalletQueryController(PointWalletRepository pointWalletRepository,
                                      PointBalanceRepository pointBalanceRepository) {
        this.pointWalletRepository = pointWalletRepository;
        this.pointBalanceRepository = pointBalanceRepository;
    }

    @GetMapping("/{pointWalletUid}/summary")
    public PointWalletSummaryResponse summary(@PathVariable String pointWalletUid) {
        PointWallet wallet = pointWalletRepository.findByPointWalletUid(pointWalletUid)
                .orElseThrow(() -> new IllegalArgumentException("point wallet not found"));
        List<BalanceResponse> balances = pointBalanceRepository.findByPointWalletIdOrderByIdAsc(wallet.getId()).stream()
                .map(BalanceResponse::from)
                .toList();
        long totalBalance = balances.stream().mapToLong(balance -> Long.parseLong(balance.balance())).sum();
        return new PointWalletSummaryResponse(wallet.getId(), wallet.getPointWalletUid(), totalBalance, balances);
    }

    public record PointWalletSummaryResponse(Long pointWalletId, String pointWalletUid,
                                             long totalBalance, List<BalanceResponse> balances) { }

    public record BalanceResponse(Long pointBalanceId, String balance) {
        private static BalanceResponse from(PointBalance balance) {
            return new BalanceResponse(balance.getId(), balance.getBalance());
        }
    }
}
