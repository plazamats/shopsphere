package com.shopsphere.server.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.shopsphere.common.dto.ShoppingDtos.PriceBreakdown;
import com.shopsphere.server.service.PricingCalculator;
import com.shopsphere.server.service.PricingConfig;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PricingCalculatorTest {

    private static final PricingConfig CFG = PricingConfig.from(Map.of(
            "vat_rate", "0.15", "discount_threshold", "1000", "discount_percent", "5",
            "shipping_flat", "60", "shipping_free_threshold", "1500", "max_item_quantity", "20", "default_low_stock", "5"));

    private static PriceBreakdown price(String subtotal) {
        return PricingCalculator.calculate(new BigDecimal(subtotal), CFG);
    }

    @Test
    void smallOrderPaysShippingAndVatButNoDiscount() {
        PriceBreakdown p = price("200.00");
        assertEquals(new BigDecimal("0.00"), p.discount());
        assertEquals(new BigDecimal("60.00"), p.shipping());
        assertEquals(new BigDecimal("30.00"), p.tax());
        assertEquals(new BigDecimal("290.00"), p.total());
    }

    @Test
    void discountStartsAtTheThreshold() {
        PriceBreakdown p = price("1000.00");
        assertEquals(new BigDecimal("50.00"), p.discount());
        assertEquals(new BigDecimal("60.00"), p.shipping());
        assertEquals(new BigDecimal("142.50"), p.tax());
        assertEquals(new BigDecimal("1152.50"), p.total());
    }

    @Test
    void justBelowTheThresholdHasNoDiscount() {
        assertEquals(new BigDecimal("0.00"), price("999.99").discount());
    }

    @Test
    void shippingIsFreeOnceTheDiscountedAmountReachesTheFreeThreshold() {
        PriceBreakdown p = price("2598.00");
        assertEquals(new BigDecimal("129.90"), p.discount());
        assertEquals(new BigDecimal("0.00"), p.shipping());
        assertEquals(new BigDecimal("2838.32"), p.total());
    }

    @Test
    void emptyCartCostsNothing() {
        PriceBreakdown p = price("0");
        assertEquals(new BigDecimal("0.00"), p.total());
        assertEquals(new BigDecimal("0.00"), p.shipping());
    }

    @Test
    void roundingIsHalfUpToTwoDecimals() {
        assertEquals(new BigDecimal("0.13"), PricingCalculator.money(new BigDecimal("0.125")));
        assertEquals(new BigDecimal("0.12"), PricingCalculator.money(new BigDecimal("0.124")));
    }
}
