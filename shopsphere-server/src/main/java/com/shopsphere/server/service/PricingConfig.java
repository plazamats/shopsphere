package com.shopsphere.server.service;

import java.math.BigDecimal;
import java.util.Map;

/** Business rules for prices, read from the system_settings table so they are not hardcoded. */
public record PricingConfig(BigDecimal vatRate, BigDecimal discountThreshold, BigDecimal discountPercent,
                            BigDecimal shippingFlat, BigDecimal shippingFreeThreshold, int maxItemQuantity,
                            int defaultLowStock) {

    public static PricingConfig from(Map<String, String> settings) {
        return new PricingConfig(
                dec(settings, "vat_rate", "0.15"),
                dec(settings, "discount_threshold", "1000.00"),
                dec(settings, "discount_percent", "5"),
                dec(settings, "shipping_flat", "60.00"),
                dec(settings, "shipping_free_threshold", "1500.00"),
                Integer.parseInt(settings.getOrDefault("max_item_quantity", "20")),
                Integer.parseInt(settings.getOrDefault("default_low_stock", "5")));
    }

    private static BigDecimal dec(Map<String, String> m, String key, String fallback) {
        return new BigDecimal(m.getOrDefault(key, fallback));
    }
}
