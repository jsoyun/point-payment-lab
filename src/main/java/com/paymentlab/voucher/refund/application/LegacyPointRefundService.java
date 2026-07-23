package com.paymentlab.voucher.refund.application;

import com.paymentlab.voucher.common.Money;
import com.paymentlab.voucher.provider.VoucherProviderClient;
import com.paymentlab.voucher.provider.VoucherProviderClient.CancelVoucherRequest;
import com.paymentlab.voucher.payment.domain.PointCredit;
import com.paymentlab.voucher.payment.domain.PointLedger;
import com.paymentlab.voucher.payment.domain.PointLot;
import com.paymentlab.voucher.payment.domain.VoucherPurchase;
import com.paymentlab.voucher.payment.domain.PointSourceBalance;
import com.paymentlab.voucher.payment.domain.VoucherProduct;
import com.paymentlab.voucher.payment.domain.PointBalance;
import com.paymentlab.voucher.payment.domain.repository.PointCreditRepository;
import com.paymentlab.voucher.payment.domain.repository.PointLedgerRepository;
import com.paymentlab.voucher.payment.domain.repository.PointLotRepository;
import com.paymentlab.voucher.payment.domain.repository.VoucherPurchaseRepository;
import com.paymentlab.voucher.payment.domain.repository.PointSourceBalanceRepository;
import com.paymentlab.voucher.payment.domain.repository.VoucherProductRepository;
import com.paymentlab.voucher.payment.domain.repository.PointBalanceRepository;
import com.paymentlab.voucher.refund.api.PointRefundController.PointRefundRequest;
import com.paymentlab.voucher.refund.api.PointRefundController.PointRefundResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class LegacyPointRefundService {

    private final VoucherPurchaseRepository voucherPurchaseRepository;
    private final PointLedgerRepository pointLedgerRepository;
    private final PointLotRepository pointLotRepository;
    private final PointCreditRepository pointCreditRepository;
    private final PointBalanceRepository pointBalanceRepository;
    private final PointSourceBalanceRepository pointSourceBalanceRepository;
    private final VoucherProductRepository voucherProductRepository;
    private final VoucherProviderClient voucherProviderClient;
    private final TransactionTemplate transactionTemplate;

    public LegacyPointRefundService(
            VoucherPurchaseRepository voucherPurchaseRepository,
            PointLedgerRepository pointLedgerRepository,
            PointLotRepository pointLotRepository,
            PointCreditRepository pointCreditRepository,
            PointBalanceRepository pointBalanceRepository,
            PointSourceBalanceRepository pointSourceBalanceRepository,
            VoucherProductRepository voucherProductRepository,
            VoucherProviderClient voucherProviderClient,
            PlatformTransactionManager transactionManager
    ) {
        this.voucherPurchaseRepository = voucherPurchaseRepository;
        this.pointLedgerRepository = pointLedgerRepository;
        this.pointLotRepository = pointLotRepository;
        this.pointCreditRepository = pointCreditRepository;
        this.pointBalanceRepository = pointBalanceRepository;
        this.pointSourceBalanceRepository = pointSourceBalanceRepository;
        this.voucherProductRepository = voucherProductRepository;
        this.voucherProviderClient = voucherProviderClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public PointRefundResponse refund(PointRefundRequest request) {
        VoucherPurchase withdrawal = voucherPurchaseRepository.findByVoucherNumber(request.voucherNumber())
                .orElseThrow(() -> new IllegalArgumentException("voucher number not found"));
        VoucherProduct voucherProduct = voucherProductRepository.findById(withdrawal.getVoucherProductId())
                .orElseThrow(() -> new IllegalArgumentException("voucher product not found"));

        PointRefundResponse response = transactionTemplate.execute(status ->
                restorePointTransaction(withdrawal)
        );

        voucherProviderClient.cancel(new CancelVoucherRequest(
                voucherProduct.getVoucherProductCode(),
                withdrawal.getVoucherNumber()
        ));

        return response;
    }

    private PointRefundResponse restorePointTransaction(VoucherPurchase withdrawal) {
        PointLedger paymentHistory = pointLedgerRepository.findById(withdrawal.getPointLedgerId())
                .orElseThrow(() -> new IllegalStateException("payment history not found"));
        PointBalance pointBalance = pointBalanceRepository.findById(paymentHistory.getPointBalanceId())
                .orElseThrow(() -> new IllegalStateException("point balance not found"));

        List<PointLot> usedLots = pointLotRepository.findByVoucherNumber(withdrawal.getVoucherNumber());
        long refundPoint = usedLots.stream()
                .mapToLong(lot -> Money.parse(lot.getAmount()))
                .sum();

        withdrawal.cancel();
        voucherPurchaseRepository.save(withdrawal);

        pointBalance.add(refundPoint);
        pointBalanceRepository.save(pointBalance);

        for (PointLot lot : usedLots) {
            PointSourceBalance sourceBalance = pointSourceBalanceRepository.findById(lot.getPointSourceBalanceId())
                    .orElseThrow(() -> new IllegalStateException("sourceBalance not found"));
            sourceBalance.add(Money.parse(lot.getAmount()));
            pointSourceBalanceRepository.save(sourceBalance);

            lot.restore();
            pointLotRepository.save(lot);
        }

        PointLedger returnHistory = PointLedger.returned(
                paymentHistory.getPointWalletId(),
                pointBalance.getId(),
                refundPoint,
                pointBalance.getBalance(),
                "포인트 바우처 환불"
        );
        pointLedgerRepository.save(returnHistory);
        pointCreditRepository.save(PointCredit.returned(
                withdrawal.getVoucherNumber(),
                refundPoint,
                returnHistory.getId()
        ));

        return new PointRefundResponse("success", withdrawal.getVoucherNumber(), refundPoint);
    }
}
