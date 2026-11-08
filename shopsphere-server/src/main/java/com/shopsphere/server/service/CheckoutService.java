package com.shopsphere.server.service;

import com.shopsphere.common.dto.AuthDtos.AddressDto;
import com.shopsphere.common.dto.ShoppingDtos.CartItemDto;
import com.shopsphere.common.dto.ShoppingDtos.CheckoutRequest;
import com.shopsphere.common.dto.ShoppingDtos.CheckoutResult;
import com.shopsphere.common.dto.ShoppingDtos.OrderDto;
import com.shopsphere.common.dto.ShoppingDtos.OrderItemDto;
import com.shopsphere.common.dto.ShoppingDtos.OrderQuery;
import com.shopsphere.common.dto.ShoppingDtos.OrderStatusRequest;
import com.shopsphere.common.dto.ShoppingDtos.PaymentDto;
import com.shopsphere.common.dto.ShoppingDtos.PriceBreakdown;
import com.shopsphere.common.protocol.ErrorCode;
import com.shopsphere.common.protocol.OrderStatus;
import com.shopsphere.common.protocol.PaymentStatus;
import com.shopsphere.common.protocol.StockReason;
import com.shopsphere.server.dao.AddressDao;
import com.shopsphere.server.dao.CartDao;
import com.shopsphere.server.dao.CartDao.CartRow;
import com.shopsphere.server.dao.InventoryDao;
import com.shopsphere.server.dao.OrderDao;
import com.shopsphere.server.dao.OrderDao.OrderHeader;
import com.shopsphere.server.dao.PaymentDao;
import com.shopsphere.server.dao.SettingsDao;
import com.shopsphere.server.database.Database;
import com.shopsphere.server.security.Session;
import com.shopsphere.server.service.PaymentGateway.PaymentRequest;
import com.shopsphere.server.service.PaymentGateway.PaymentResult;
import com.shopsphere.server.validation.Validator;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Checkout, cancellation and order management. Checkout is one database transaction:
 * the cart is locked, stock is taken with a guarded atomic update (so it can never be oversold),
 * the order and its item snapshots are written, then the simulated payment is attempted.
 * A repeated request with the same idempotency key returns the original order.
 */
public class CheckoutService {

    private final Database db;
    private final CartDao carts;
    private final AddressDao addresses;
    private final InventoryDao inventory;
    private final OrderDao orders;
    private final PaymentDao payments;
    private final SettingsDao settings;
    private final PaymentGateway gateway;
    private final AuditService audit;

    public CheckoutService(Database db, CartDao carts, AddressDao addresses, InventoryDao inventory, OrderDao orders,
                           PaymentDao payments, SettingsDao settings, PaymentGateway gateway, AuditService audit) {
        this.db = db;
        this.carts = carts;
        this.addresses = addresses;
        this.inventory = inventory;
        this.orders = orders;
        this.payments = payments;
        this.settings = settings;
        this.gateway = gateway;
        this.audit = audit;
    }

    public CheckoutResult checkout(Session s, CheckoutRequest r) {
        if (r == null) throw ShopException.invalid("Checkout details are required");
        String key = Validator.idempotencyKey(r.idempotencyKey());
        String digits = Validator.cardNumber(r.cardNumber());
        return db.transaction(c -> {
            long cartId = carts.lockCart(c, s.userId());
            var existing = orders.findByKey(c, s.userId(), key);
            if (existing.isPresent()) {
                OrderDto o = full(c, existing.get().id());
                boolean ok = o.status() != OrderStatus.PAYMENT_FAILED;
                return new CheckoutResult(o, ok, ok ? "This order was already placed" : "Payment failed earlier");
            }
            AddressDto ship = addresses.findOwned(c, r.addressId(), s.userId())
                    .orElseThrow(() -> ShopException.invalid("Choose one of your saved delivery addresses"));
            List<CartRow> rows = carts.items(c, cartId, true);
            if (rows.isEmpty()) throw ShopException.invalid("Your cart is empty");

            List<Long> ids = rows.stream().map(CartRow::productId).sorted().collect(Collectors.toList());
            Map<Long, Integer> stock = inventory.lockQuantities(c, ids);
            BigDecimal subtotal = BigDecimal.ZERO;
            for (CartRow row : rows) {
                if (!row.active()) throw new ShopException(ErrorCode.CONFLICT, row.name() + " is no longer available");
                int have = stock.getOrDefault(row.productId(), 0);
                if (have < row.quantity()) {
                    throw new ShopException(ErrorCode.OUT_OF_STOCK, have == 0
                            ? row.name() + " is out of stock"
                            : "Only " + have + " of " + row.name() + " left in stock");
                }
                subtotal = subtotal.add(PricingCalculator.money(row.unitPrice().multiply(BigDecimal.valueOf(row.quantity()))));
            }
            PricingConfig cfg = PricingConfig.from(settings.asMap(c));
            PriceBreakdown price = PricingCalculator.calculate(subtotal, cfg);

            String number = orders.nextOrderNumber(c);
            long orderId = orders.insert(c, s.userId(), number, OrderStatus.PENDING_PAYMENT, price, ship, key);
            for (CartRow row : rows) {
                BigDecimal line = PricingCalculator.money(row.unitPrice().multiply(BigDecimal.valueOf(row.quantity())));
                orders.insertItem(c, orderId, row.productId(), row.sku(), row.name(), row.unitPrice(), row.quantity(), line);
                if (!inventory.decrementIfAvailable(c, row.productId(), row.quantity())) {
                    throw new ShopException(ErrorCode.OUT_OF_STOCK, "Only limited stock of " + row.name() + " is left");
                }
                inventory.addMovement(c, row.productId(), -row.quantity(), StockReason.PURCHASE, number, null, s.userId());
            }

            PaymentResult result = gateway.charge(new PaymentRequest(number, price.total(), digits));
            String last4 = digits.substring(digits.length() - 4);
            if (result.success()) {
                payments.insert(c, orderId, result.reference(), price.total(), PaymentStatus.PAID, "SIMULATED_CARD", last4, null);
                orders.updateStatus(c, orderId, OrderStatus.PAID);
                carts.clear(c, cartId);
                return new CheckoutResult(full(c, orderId), true, "Payment successful (simulation)");
            }
            payments.insert(c, orderId, result.reference(), price.total(), PaymentStatus.FAILED, "SIMULATED_CARD", last4,
                    result.failureReason());
            orders.updateStatus(c, orderId, OrderStatus.PAYMENT_FAILED);
            for (CartRow row : rows) {
                inventory.increment(c, row.productId(), row.quantity());
                inventory.addMovement(c, row.productId(), row.quantity(), StockReason.PAYMENT_FAILED, number, null, s.userId());
            }
            return new CheckoutResult(full(c, orderId), false, "Payment failed: " + result.failureReason() + " (simulation)");
        });
    }

    public ArrayList<OrderDto> myOrders(Session s) {
        return db.read(c -> orders.listByUser(c, s.userId()));
    }

    public OrderDto get(Session s, long orderId) {
        return db.read(c -> {
            OrderDto o = orders.find(c, orderId).orElseThrow(() -> ShopException.notFound("Order"));
            ensureAccess(c, s, orderId);
            return full(c, o.id());
        });
    }

    /** A plain text receipt, built on the server. */
    public String receipt(Session s, long orderId) {
        OrderDto o = get(s, orderId);
        if (o.status() == OrderStatus.PENDING_PAYMENT || o.status() == OrderStatus.PAYMENT_FAILED) {
            throw new ShopException(ErrorCode.CONFLICT, "A receipt is only available for paid orders");
        }
        StringBuilder b = new StringBuilder();
        b.append("SHOPSPHERE RECEIPT\n");
        b.append("(Simulated payment. No real money was charged.)\n\n");
        b.append("Order:    ").append(o.orderNumber()).append('\n');
        b.append("Date:     ").append(o.createdAt()).append('\n');
        b.append("Status:   ").append(o.status()).append('\n');
        b.append("Ship to:  ").append(o.shipTo().oneLine()).append("\n\n");
        for (OrderItemDto i : o.items()) {
            b.append(String.format("%-34s %3d x %10s = %11s%n", trim(i.name(), 34), i.quantity(), i.unitPrice(), i.lineTotal()));
        }
        PriceBreakdown p = o.pricing();
        b.append('\n');
        b.append(String.format("%-52s %11s%n", "Subtotal", p.subtotal()));
        b.append(String.format("%-52s %11s%n", "Discount", "-" + p.discount()));
        b.append(String.format("%-52s %11s%n", "Shipping", p.shipping()));
        b.append(String.format("%-52s %11s%n", "VAT", p.tax()));
        b.append(String.format("%-52s %11s%n", "TOTAL", p.total()));
        if (o.payment() != null) {
            b.append("\nPayment reference: ").append(o.payment().reference()).append(" (").append(o.payment().status()).append(")\n");
        }
        return b.toString();
    }

    public OrderDto cancel(Session s, long orderId) {
        return db.transaction(c -> {
            OrderHeader h = orders.header(c, orderId, true).orElseThrow(() -> ShopException.notFound("Order"));
            if (h.userId() != s.userId()) throw ShopException.notFound("Order");
            if (!h.status().customerMayCancel()) {
                throw new ShopException(ErrorCode.CONFLICT, "This order can no longer be cancelled");
            }
            cancelInternal(c, h, s.userId());
            audit.security(c, s.userId(), s.email(), "ORDER_CANCELLED_BY_CUSTOMER", "ORDER", h.orderNumber(), null);
            return full(c, orderId);
        });
    }

    public ArrayList<OrderDto> listAll(OrderQuery q) {
        OrderQuery query = q == null ? new OrderQuery(null, null) : q;
        String search = Validator.search(query.search());
        return db.read(c -> orders.listAll(c, query.status(), search));
    }

    public OrderDto updateStatus(Session actor, OrderStatusRequest r) {
        if (r == null || r.status() == null) throw ShopException.invalid("Order and status are required");
        return db.transaction(c -> {
            OrderHeader h = orders.header(c, r.orderId(), true).orElseThrow(() -> ShopException.notFound("Order"));
            if (!h.status().canTransitionTo(r.status())) {
                throw new ShopException(ErrorCode.CONFLICT, "An order cannot move from " + h.status() + " to " + r.status());
            }
            if (r.status() == OrderStatus.CANCELLED) {
                cancelInternal(c, h, actor.userId());
            } else {
                orders.updateStatus(c, h.id(), r.status());
            }
            audit.admin(c, actor, "ORDER_STATUS_CHANGED", "ORDER", h.orderNumber(), h.status() + " to " + r.status());
            return full(c, h.id());
        });
    }

    /** Restore the stock and refund the simulated payment. The caller has locked the order row. */
    private void cancelInternal(Connection c, OrderHeader h, long actorId) throws SQLException {
        List<OrderItemDto> items = orders.items(c, h.id());
        List<Long> ids = items.stream().map(OrderItemDto::productId).sorted().collect(Collectors.toList());
        inventory.lockQuantities(c, ids);
        for (OrderItemDto i : items) {
            inventory.increment(c, i.productId(), i.quantity());
            inventory.addMovement(c, i.productId(), i.quantity(), StockReason.CANCELLATION, h.orderNumber(), null, actorId);
        }
        payments.markRefunded(c, h.id());
        orders.updateStatus(c, h.id(), OrderStatus.CANCELLED);
    }

    private void ensureAccess(Connection c, Session s, long orderId) throws SQLException {
        if (s.role().isAdmin()) return;
        OrderHeader h = orders.header(c, orderId, false).orElseThrow(() -> ShopException.notFound("Order"));
        if (h.userId() != s.userId()) throw ShopException.notFound("Order");
    }

    private OrderDto full(Connection c, long id) throws SQLException {
        OrderDto o = orders.find(c, id).orElseThrow();
        PaymentDto p = payments.findByOrder(c, id).orElse(null);
        return new OrderDto(o.id(), o.orderNumber(), o.status(), o.createdAt(), o.customerEmail(), o.pricing(),
                o.shipTo(), orders.items(c, id), p);
    }

    private static String trim(String v, int max) {
        return v.length() <= max ? v : v.substring(0, max - 1) + ".";
    }
}
