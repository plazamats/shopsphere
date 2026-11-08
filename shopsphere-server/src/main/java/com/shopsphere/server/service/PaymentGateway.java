package com.shopsphere.server.service;

import java.math.BigDecimal;

/**
 * The payment provider boundary. Version 1 only has a simulation. A real provider can later be added
 * by writing another implementation, without changing CheckoutService.
 */
public interface PaymentGateway {

    record PaymentRequest(String orderNumber, BigDecimal amount, String cardDigits) { }

    record PaymentResult(boolean success, String reference, String failureReason) { }

    PaymentResult charge(PaymentRequest request);
}
