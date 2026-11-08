package com.shopsphere.server.service;

import java.util.UUID;

/**
 * SIMULATION ONLY. No real money is moved and no card details are stored or sent anywhere.
 * Test cards: a number ending in 0002 is declined, one ending in 9995 has insufficient funds,
 * every other valid number is approved (for example 4242 4242 4242 4242).
 */
public class SimulatedPaymentGateway implements PaymentGateway {

    @Override
    public PaymentResult charge(PaymentRequest request) {
        String reference = "SIM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        String digits = request.cardDigits();
        if (digits.endsWith("0002")) return new PaymentResult(false, reference, "Card declined");
        if (digits.endsWith("9995")) return new PaymentResult(false, reference, "Insufficient funds");
        return new PaymentResult(true, reference, null);
    }
}
