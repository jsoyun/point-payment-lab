package com.paymentlab.voucher.payment.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentlab.voucher.payment.api.PointPaymentController.PointPaymentResponse;
import org.springframework.stereotype.Component;

@Component
public class PaymentResponseCodec {

    private final ObjectMapper objectMapper;

    public PaymentResponseCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String serialize(PointPaymentResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("payment response serialization failed", error);
        }
    }

    public PointPaymentResponse deserialize(String body) {
        try {
            return objectMapper.readValue(body, PointPaymentResponse.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("payment response deserialization failed", error);
        }
    }
}
