package com.shopsphere.server.service;

import com.shopsphere.common.dto.ShoppingDtos.PriceBreakdown;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates every price on the server with BigDecimal. The rules are:
 * a percentage discount when the subtotal reaches a threshold, VAT on the discounted amount,
 * and a flat shipping fee that is free above a threshold.
 */
public final class PricingCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private PricingCalculator() { }

    public static PriceBreakdown calculate(BigDecimal subtotal, PricingConfig cfg) {
        subtotal = money(subtotal);
        BigDecimal discount = money(BigDecimal.ZERO);
        if (subtotal.signum() > 0 && subtotal.compareTo(cfg.discountThreshold()) >= 0) {
            discount = money(subtotal.multiply(cfg.discountPercent()).divide(HUNDRED, 10, RoundingMode.HALF_UP));
        }
        BigDecimal net = subtotal.subtract(discount);
        BigDecimal tax = money(net.multiply(cfg.vatRate()));
        BigDecimal shipping = BigDecimal.ZERO;
        if (subtotal.signum() > 0 && net.compareTo(cfg.shippingFreeThreshold()) < 0) {
            shipping = money(cfg.shippingFlat());
        }
        BigDecimal total = net.add(tax).add(shipping);
        return new PriceBreakdown(subtotal, discount, money(shipping), tax, money(total));
    }

    /** Two decimals, rounded half up. */
    public static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
