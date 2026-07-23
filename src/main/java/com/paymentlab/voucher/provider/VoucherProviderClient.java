package com.paymentlab.voucher.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class VoucherProviderClient {

    private final RestClient restClient;

    public VoucherProviderClient(@Value("${voucher-provider.mock-base-url}") String mockBaseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(mockBaseUrl)
                .build();
    }

    public IssueVoucherResponse issue(IssueVoucherRequest request) {
        return restClient.post()
                .uri("/vouchers/issue")
                .body(request)
                .retrieve()
                .body(IssueVoucherResponse.class);
    }

    public void cancel(CancelVoucherRequest request) {
        restClient.post()
                .uri("/vouchers/cancel")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public record IssueVoucherRequest(String voucherProductCode, String orderId) {
    }

    public record IssueVoucherResponse(String resultCode, String voucherNumber, String pinNumber) {
    }

    public record CancelVoucherRequest(String voucherProductCode, String voucherNumber) {
    }
}
