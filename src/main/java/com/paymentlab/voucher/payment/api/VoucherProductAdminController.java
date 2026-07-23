package com.paymentlab.voucher.payment.api;

import com.paymentlab.voucher.payment.domain.VoucherProduct;
import com.paymentlab.voucher.payment.domain.repository.VoucherProductRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/voucher-products")
public class VoucherProductAdminController {

    private final VoucherProductRepository voucherProductRepository;

    public VoucherProductAdminController(VoucherProductRepository voucherProductRepository) {
        this.voucherProductRepository = voucherProductRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VoucherProductResponse create(@Valid @RequestBody CreateVoucherProductRequest request) {
        voucherProductRepository.findByVoucherProductCode(request.voucherProductCode())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("voucher product code already exists");
                });

        VoucherProduct saved = voucherProductRepository.save(VoucherProduct.create(
                request.voucherProductCode(),
                request.voucherName(),
                request.sellPrice(),
                request.useTerm()
        ));

        return VoucherProductResponse.from(saved);
    }

    @GetMapping
    public java.util.List<VoucherProductResponse> findAll() {
        return voucherProductRepository.findAll().stream()
                .map(VoucherProductResponse::from)
                .toList();
    }

    public record CreateVoucherProductRequest(
            @NotBlank String voucherProductCode,
            @NotBlank String voucherName,
            @Min(1) long sellPrice,
            @Min(1) int useTerm
    ) {
    }

    public record VoucherProductResponse(
            Long id,
            String voucherProductCode,
            String voucherName,
            long sellPrice,
            int useTerm
    ) {
        private static VoucherProductResponse from(VoucherProduct voucherProduct) {
            return new VoucherProductResponse(
                    voucherProduct.getId(),
                    voucherProduct.getVoucherProductCode(),
                    voucherProduct.getVoucherName(),
                    voucherProduct.getSellPrice(),
                    voucherProduct.getUseTerm()
            );
        }
    }
}
