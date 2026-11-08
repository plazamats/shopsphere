package com.shopsphere.server.dao;

import com.shopsphere.common.dto.ShoppingDtos.PaymentDto;
import com.shopsphere.common.protocol.PaymentStatus;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Optional;

public class PaymentDao {

    private static final String SELECT =
            "SELECT p.id, o.order_number, p.reference, p.amount, p.status, p.method, p.simulated, p.failure_reason, "
            + "p.created_at FROM payments p JOIN orders o ON o.id = p.order_id ";

    public long insert(Connection c, long orderId, String reference, BigDecimal amount, PaymentStatus status,
                       String method, String cardLast4, String failureReason) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO payments (order_id, reference, amount, status, method, card_last4, simulated, failure_reason) "
                + "VALUES (?, ?, ?, ?, ?, ?, TRUE, ?) RETURNING id")) {
            ps.setLong(1, orderId);
            ps.setString(2, reference);
            ps.setBigDecimal(3, amount);
            ps.setString(4, status.name());
            ps.setString(5, method);
            ps.setString(6, cardLast4);
            ps.setString(7, failureReason);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** The newest payment of an order. */
    public Optional<PaymentDto> findByOrder(Connection c, long orderId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + "WHERE p.order_id = ? ORDER BY p.id DESC LIMIT 1")) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public ArrayList<PaymentDto> listAll(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + "ORDER BY p.id DESC LIMIT 500");
             ResultSet rs = ps.executeQuery()) {
            ArrayList<PaymentDto> out = new ArrayList<>();
            while (rs.next()) out.add(map(rs));
            return out;
        }
    }

    public void markRefunded(Connection c, long orderId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE payments SET status = 'REFUNDED', updated_at = now() WHERE order_id = ? AND status = 'PAID'")) {
            ps.setLong(1, orderId);
            ps.executeUpdate();
        }
    }

    private PaymentDto map(ResultSet rs) throws SQLException {
        return new PaymentDto(rs.getLong("id"), rs.getString("order_number"), rs.getString("reference"),
                rs.getBigDecimal("amount"), PaymentStatus.valueOf(rs.getString("status")), rs.getString("method"),
                rs.getBoolean("simulated"), rs.getString("failure_reason"), Jdbc.instant(rs, "created_at"));
    }
}
