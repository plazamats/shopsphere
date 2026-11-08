package com.shopsphere.common.dto;

import com.shopsphere.common.dto.AuthDtos.AddressDto;
import com.shopsphere.common.protocol.OrderStatus;
import com.shopsphere.common.protocol.PaymentStatus;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;

/** DTOs for the cart, checkout, orders and payments. All prices are calculated by the server. */
public final class ShoppingDtos {

    private ShoppingDtos() { }

    /** The client only sends product and quantity. It never sends a price. */
    public record CartUpdate(long productId, int quantity) implements Serializable { }

    public record CartItemDto(long productId, String sku, String name, BigDecimal unitPrice, int quantity,
                              BigDecimal lineTotal, int stockAvailable, boolean purchasable)
            implements Serializable { }

    /** The same pricing rules are used here and at checkout. */
    public record PriceBreakdown(BigDecimal subtotal, BigDecimal discount, BigDecimal shipping,
                                 BigDecimal tax, BigDecimal total) implements Serializable { }

    public record CartDto(ArrayList<CartItemDto> items, PriceBreakdown pricing, boolean checkoutReady)
            implements Serializable { }

    /**
     * cardNumber is a simulation. No real card data is stored: only the last four digits.
     * idempotencyKey makes sure a repeated request can never charge or order twice.
     */
    public record CheckoutRequest(long addressId, String cardNumber, String idempotencyKey) implements Serializable {
        @Override public String toString() { return "CheckoutRequest[address=" + addressId + "]"; }
    }

    public record PaymentDto(long id, String orderNumber, String reference, BigDecimal amount, PaymentStatus status,
                             String method, boolean simulated, String failureReason, Instant createdAt)
            implements Serializable { }

    public record OrderItemDto(long productId, String sku, String name, BigDecimal unitPrice, int quantity,
                               BigDecimal lineTotal) implements Serializable { }

    /** items and payment are null in list views. */
    public record OrderDto(long id, String orderNumber, OrderStatus status, Instant createdAt, String customerEmail,
                           PriceBreakdown pricing, AddressDto shipTo, ArrayList<OrderItemDto> items,
                           PaymentDto payment) implements Serializable { }

    public record CheckoutResult(OrderDto order, boolean paymentSucceeded, String message) implements Serializable { }

    // administration
    public record OrderQuery(OrderStatus status, String search) implements Serializable { }

    public record OrderStatusRequest(long orderId, OrderStatus status) implements Serializable { }
}
