package com.paymentlab.voucher.payment.application;

import com.paymentlab.voucher.common.Money;
import com.paymentlab.voucher.provider.VoucherProviderClient;
import com.paymentlab.voucher.provider.VoucherProviderClient.CancelVoucherRequest;
import com.paymentlab.voucher.provider.VoucherProviderClient.IssueVoucherRequest;
import com.paymentlab.voucher.provider.VoucherProviderClient.IssueVoucherResponse;
import com.paymentlab.voucher.payment.api.PointPaymentController.PointPaymentRequest;
import com.paymentlab.voucher.payment.api.PointPaymentController.PointPaymentResponse;
import com.paymentlab.voucher.payment.domain.PointLedger;
import com.paymentlab.voucher.payment.domain.PointLot;
import com.paymentlab.voucher.payment.domain.VoucherPurchase;
import com.paymentlab.voucher.payment.domain.PointSourceBalance;
import com.paymentlab.voucher.payment.domain.VoucherProduct;
import com.paymentlab.voucher.payment.domain.PointBalance;
import com.paymentlab.voucher.payment.domain.PointWallet;
import com.paymentlab.voucher.payment.domain.repository.PointLedgerRepository;
import com.paymentlab.voucher.payment.domain.repository.PointLotRepository;
import com.paymentlab.voucher.payment.domain.repository.VoucherPurchaseRepository;
import com.paymentlab.voucher.payment.domain.repository.PointSourceBalanceRepository;
import com.paymentlab.voucher.payment.domain.repository.VoucherProductRepository;
import com.paymentlab.voucher.payment.domain.repository.PointBalanceRepository;
import com.paymentlab.voucher.payment.domain.repository.PointWalletRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class LegacyPointPaymentService {

    private final PointWalletRepository pointWalletRepository;
    private final VoucherProductRepository voucherProductRepository;
    private final PointBalanceRepository pointBalanceRepository;
    private final PointLotRepository pointLotRepository;
    private final PointSourceBalanceRepository pointSourceBalanceRepository;
    private final PointLedgerRepository pointLedgerRepository;
    private final VoucherPurchaseRepository voucherPurchaseRepository;
    private final VoucherProviderClient voucherProviderClient;
    private final PointPaymentPreValidator pointPaymentPreValidator;
    private final PointBalanceDebitService pointBalanceDebitService;
    private final TransactionTemplate transactionTemplate;

    public LegacyPointPaymentService(
            PointWalletRepository pointWalletRepository,
            VoucherProductRepository voucherProductRepository,
            PointBalanceRepository pointBalanceRepository,
            PointLotRepository pointLotRepository,
            PointSourceBalanceRepository pointSourceBalanceRepository,
            PointLedgerRepository pointLedgerRepository,
            VoucherPurchaseRepository voucherPurchaseRepository,
            VoucherProviderClient voucherProviderClient,
            PointPaymentPreValidator pointPaymentPreValidator,
            PointBalanceDebitService pointBalanceDebitService,
            PlatformTransactionManager transactionManager
    ) {
        this.pointWalletRepository = pointWalletRepository;
        this.voucherProductRepository = voucherProductRepository;
        this.pointBalanceRepository = pointBalanceRepository;
        this.pointLotRepository = pointLotRepository;
        this.pointSourceBalanceRepository = pointSourceBalanceRepository;
        this.pointLedgerRepository = pointLedgerRepository;
        this.voucherPurchaseRepository = voucherPurchaseRepository;
        this.voucherProviderClient = voucherProviderClient;
        this.pointPaymentPreValidator = pointPaymentPreValidator;
        this.pointBalanceDebitService = pointBalanceDebitService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public PointPaymentResponse pay(PointPaymentRequest request) {
        return pay(request, false);
    }

    public PointPaymentResponse payWithConditionalDebit(PointPaymentRequest request) {
        return pay(request, true);
    }

    private PointPaymentResponse pay(PointPaymentRequest request, boolean conditionalDebit) {
        PointWallet pointWallet = pointWalletRepository.findByPointWalletUid(request.pointWalletUid())
                .orElseThrow(() -> new IllegalArgumentException("point wallet not found"));
        VoucherProduct voucherProduct = voucherProductRepository.findById(request.voucherProductId())
                .orElseThrow(() -> new IllegalArgumentException("voucher product not found"));
        PointBalance pointBalance = pointBalanceRepository.findById(request.pointBalanceId())
                .orElseThrow(() -> new IllegalArgumentException("point balance not found"));

        if (!pointBalance.getPointWalletId().equals(pointWallet.getId())) {
            throw new IllegalArgumentException("point balance does not belong to point wallet");
        }

        long usableLotTotal = pointLotRepository.findUsableLots(
                        pointWallet.getId(),
                        pointBalance.getId()
                ).stream()
                .mapToLong(lot -> Money.parse(lot.getAmount()))
                .sum();
        pointPaymentPreValidator.validate(
                request.point(),
                voucherProduct.getSellPrice(),
                Money.parse(pointBalance.getBalance()),
                usableLotTotal
        );

        IssueVoucherResponse issuedVoucher = voucherProviderClient.issue(new IssueVoucherRequest(
                voucherProduct.getVoucherProductCode(),
                request.orderId()
        ));

        try {
            return transactionTemplate.execute(status ->
                    savePaymentTransaction(
                            request, pointWallet, voucherProduct, pointBalance,
                            issuedVoucher, conditionalDebit
                    )
            );

            //DB중복된 orderId로 인한 DataIntegrityViolationException 발생 시 예외 처리
            // TODO:  발급된 바우처를 취소하고 예외를 다시 던집니다.
        } catch (DataIntegrityViolationException duplicateOrConstraintFailure) {
            voucherProviderClient.cancel(new CancelVoucherRequest(
                    voucherProduct.getVoucherProductCode(),
                    issuedVoucher.voucherNumber()
            ));
            throw duplicateOrConstraintFailure;
            //일반 DB트랜잭션 처리 중 발생한 RuntimeException 예외 처리
        } catch (RuntimeException dbFailure) {
            voucherProviderClient.cancel(new CancelVoucherRequest(
                    voucherProduct.getVoucherProductCode(),
                    issuedVoucher.voucherNumber()
            ));
            throw dbFailure;
        }
    }

    private PointPaymentResponse savePaymentTransaction(
            PointPaymentRequest request,
            PointWallet pointWallet,
            VoucherProduct voucherProduct,
            PointBalance pointBalance,
            IssueVoucherResponse issuedVoucher,
            boolean conditionalDebit
    ) {
        PointBalance paymentBalance = conditionalDebit
                ? pointBalanceDebitService.debit(
                        pointBalance.getId(), pointWallet.getId(), request.point()
                )
                : pointBalance;

        List<PointLot> usableLots = pointLotRepository.findUsableLots(
                pointWallet.getId(),
                paymentBalance.getId()
        );

        long remain = request.point();
        long used = 0;

        for (PointLot lot : usableLots) {
            if (remain == 0) {
                break;
            }

            long amount = Money.parse(lot.getAmount());
            long useAmount = Math.min(amount, remain);

            lot.markUsed(String.valueOf(useAmount), issuedVoucher.voucherNumber());
            pointLotRepository.save(lot);

            PointSourceBalance sourceBalance = pointSourceBalanceRepository.findById(lot.getPointSourceBalanceId())
                    .orElseThrow(() -> new IllegalStateException("sourceBalance not found"));
            sourceBalance.subtract(useAmount);
            pointSourceBalanceRepository.save(sourceBalance);

            if (amount > useAmount) {
                PointLot rest = PointLot.restOf(lot, amount - useAmount);
                pointLotRepository.save(rest);
            }

            remain -= useAmount;
            used += useAmount;
        }

        if (!conditionalDebit) {
            paymentBalance.subtract(request.point());
            pointBalanceRepository.save(paymentBalance);
        }

        PointLedger history = PointLedger.withdrawal(
                pointWallet.getId(),
                paymentBalance.getId(),
                request.point(),
                paymentBalance.getBalance(),
                "포인트 바우처 구매"
        );
        pointLedgerRepository.save(history);

        VoucherPurchase withdrawal = VoucherPurchase.pointPayment(
                issuedVoucher.voucherNumber(),
                issuedVoucher.pinNumber(),
                request.orderId(),
                voucherProduct.getId(),
                history.getId(),
                request.point(),
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(voucherProduct.getUseTerm())
        );
        voucherPurchaseRepository.save(withdrawal);

        if (used < request.point()) {
            throw new IllegalStateException("usable point lots are less than requested point");
        }

        return new PointPaymentResponse(
                "A voucher has been issued successfully",
                request.orderId(),
                issuedVoucher.voucherNumber(),
                issuedVoucher.pinNumber(),
                request.point(),
                paymentBalance.getBalance()
        );
    }
}
