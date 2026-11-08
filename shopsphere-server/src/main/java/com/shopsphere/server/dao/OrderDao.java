package com.shopsphere.server.dao;

import com.shopsphere.common.dto.AuthDtos.AddressDto;
import com.shopsphere.common.dto.ShoppingDtos.OrderDto;
import com.shopsphere.common.dto.ShoppingDtos.OrderItemDto;
import com.shopsphere.common.dto.ShoppingDtos.PriceBreakdown;
import com.shopsphere.common.protocol.OrderStatus;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Optional;

public class OrderDao {

    /** The owner and status of an order, used for permission and state checks. */
    public record OrderHeader(long id, long userId, String orderNumber, OrderStatus status) { }

    private static final String SELECT =
            "SELECT o.id, o.order_number, o.status, o.created_at, u.email, o.subtotal, o.discount, o.shipping, o.tax, o.total, "
            + "o.ship_recipient, o.ship_line1, o.ship_line2, o.ship_city, o.ship_province, o.ship_postal, o.ship_country, "
            + "o.ship_phone FROM orders o JOIN users u ON u.id = o.user_id ";

    /** A unique, readable order number such as SS-20261107-001042. */
    public String nextOrderNumber(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT nextval('order_number_seq')");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return "SS-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%06d", rs.getLong(1));
        }
    }

    public long insert(Connection c, long userId, String orderNumber, OrderStatus status, PriceBreakdown p,
                       AddressDto ship, String idempotencyKey) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO orders (order_number, user_id, status, subtotal, discount, shipping, tax, total, ship_recipient, "
                + "ship_line1, ship_line2, ship_city, ship_province, ship_postal, ship_country, ship_phone, idempotency_key) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id")) {
            int i = 1;
            ps.setString(i++, orderNumber);
            ps.setLong(i++, userId);
            ps.setString(i++, status.name());
            ps.setBigDecimal(i++, p.subtotal());
            ps.setBigDecimal(i++, p.discount());
            ps.setBigDecimal(i++, p.shipping());
            ps.setBigDecimal(i++, p.tax());
            ps.setBigDecimal(i++, p.total());
            ps.setString(i++, ship.recipient());
            ps.setString(i++, ship.line1());
            ps.setString(i++, ship.line2());
            ps.setString(i++, ship.city());
            ps.setString(i++, ship.province());
            ps.setString(i++, ship.postalCode());
            ps.setString(i++, ship.country());
            ps.setString(i++, ship.phone());
            ps.setString(i, idempotencyKey);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** The name, SKU and price are copied into the order item so history stays correct. */
    public void insertItem(Connection c, long orderId, long productId, String sku, String name, BigDecimal unitPrice,
                           int quantity, BigDecimal lineTotal) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO order_items (order_id, product_id, sku, product_name, unit_price, quantity, line_total) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, orderId);
            ps.setLong(2, productId);
            ps.setString(3, sku);
            ps.setString(4, name);
            ps.setBigDecimal(5, unitPrice);
            ps.setInt(6, quantity);
            ps.setBigDecimal(7, lineTotal);
            ps.executeUpdate();
        }
    }

    public ArrayList<OrderItemDto> items(Connection c, long orderId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT product_id, sku, product_name, unit_price, quantity, line_total FROM order_items "
                + "WHERE order_id = ? ORDER BY id")) {
            ps.setLong(1, orderId);
            ArrayList<OrderItemDto> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new OrderItemDto(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4),
                            rs.getInt(5), rs.getBigDecimal(6)));
                }
            }
            return out;
        }
    }

    public Optional<OrderDto> find(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + "WHERE o.id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<OrderDto> findByKey(Connection c, long userId, String key) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + "WHERE o.user_id = ? AND o.idempotency_key = ?")) {
            ps.setLong(1, userId);
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** Read the owner and status. With forUpdate the row is locked until the transaction ends. */
    public Optional<OrderHeader> header(Connection c, long id, boolean forUpdate) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, user_id, order_number, status FROM orders WHERE id = ?" + (forUpdate ? " FOR UPDATE" : ""))) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new OrderHeader(rs.getLong(1), rs.getLong(2), rs.getString(3), OrderStatus.valueOf(rs.getString(4))))
                        : Optional.empty();
            }
        }
    }

    public void updateStatus(Connection c, long id, OrderStatus status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE orders SET status = ?, updated_at = now() WHERE id = ?")) {
            ps.setString(1, status.name());
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public ArrayList<OrderDto> listByUser(Connection c, long userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + "WHERE o.user_id = ? ORDER BY o.created_at DESC, o.id DESC")) {
            ps.setLong(1, userId);
            return readAll(ps);
        }
    }

    /** Orders of all customers, optionally filtered by status and by order number or customer email. */
    public ArrayList<OrderDto> listAll(Connection c, OrderStatus status, String search) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT
                + "WHERE (CAST(? AS VARCHAR) IS NULL OR o.status = ?) AND (CAST(? AS VARCHAR) IS NULL "
                + "OR lower(o.order_number) LIKE ? ESCAPE '\\' OR lower(u.email) LIKE ? ESCAPE '\\') "
                + "ORDER BY o.created_at DESC, o.id DESC LIMIT 500")) {
            String s = status == null ? null : status.name();
            ps.setString(1, s);
            ps.setString(2, s);
            String pattern = search == null ? null : Jdbc.contains(search);
            ps.setString(3, search);
            ps.setString(4, pattern);
            ps.setString(5, pattern);
            return readAll(ps);
        }
    }

    /** True if the customer has a delivered order that contains the product (verified purchase). */
    public boolean hasReceivedProduct(Connection c, long userId, long productId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM orders o JOIN order_items oi ON oi.order_id = o.id "
                + "WHERE o.user_id = ? AND oi.product_id = ? AND o.status = 'DELIVERED' LIMIT 1")) {
            ps.setLong(1, userId);
            ps.setLong(2, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private ArrayList<OrderDto> readAll(PreparedStatement ps) throws SQLException {
        ArrayList<OrderDto> out = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(map(rs));
        }
        return out;
    }

    private OrderDto map(ResultSet rs) throws SQLException {
        PriceBreakdown p = new PriceBreakdown(rs.getBigDecimal("subtotal"), rs.getBigDecimal("discount"),
                rs.getBigDecimal("shipping"), rs.getBigDecimal("tax"), rs.getBigDecimal("total"));
        AddressDto ship = new AddressDto(null, rs.getString("ship_recipient"), rs.getString("ship_line1"),
                rs.getString("ship_line2"), rs.getString("ship_city"), rs.getString("ship_province"),
                rs.getString("ship_postal"), rs.getString("ship_country"), rs.getString("ship_phone"));
        return new OrderDto(rs.getLong("id"), rs.getString("order_number"), OrderStatus.valueOf(rs.getString("status")),
                Jdbc.instant(rs, "created_at"), rs.getString("email"), p, ship, null, null);
    }
}
