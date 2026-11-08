package com.shopsphere.server.dao;

import com.shopsphere.common.dto.AdminDtos.InventoryDto;
import com.shopsphere.common.dto.AdminDtos.StockMovementDto;
import com.shopsphere.common.protocol.StockReason;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InventoryDao {

    public void insert(Connection c, long productId, int quantity, int lowStockThreshold) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO inventory (product_id, quantity, low_stock_threshold) VALUES (?, ?, ?)")) {
            ps.setLong(1, productId);
            ps.setInt(2, quantity);
            ps.setInt(3, lowStockThreshold);
            ps.executeUpdate();
        }
    }

    /**
     * Lock the stock rows of these products, in id order so two checkouts cannot deadlock.
     * Returns product id to quantity.
     */
    public Map<Long, Integer> lockQuantities(Connection c, List<Long> productIds) throws SQLException {
        Map<Long, Integer> out = new LinkedHashMap<>();
        if (productIds.isEmpty()) return out;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT product_id, quantity FROM inventory WHERE product_id = ANY (?) ORDER BY product_id FOR UPDATE")) {
            ps.setArray(1, c.createArrayOf("bigint", productIds.toArray(new Long[0])));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.put(rs.getLong(1), rs.getInt(2));
            }
        }
        return out;
    }

    /**
     * Remove stock only if enough is available. The condition is in the UPDATE itself, so stock
     * can never go below zero, even if two customers buy at the same moment.
     */
    public boolean decrementIfAvailable(Connection c, long productId, int quantity) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE inventory SET quantity = quantity - ?, updated_at = now() WHERE product_id = ? AND quantity >= ?")) {
            ps.setInt(1, quantity);
            ps.setLong(2, productId);
            ps.setInt(3, quantity);
            return ps.executeUpdate() == 1;
        }
    }

    public void increment(Connection c, long productId, int quantity) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE inventory SET quantity = quantity + ?, updated_at = now() WHERE product_id = ?")) {
            ps.setInt(1, quantity);
            ps.setLong(2, productId);
            ps.executeUpdate();
        }
    }

    public int quantity(Connection c, long productId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT quantity FROM inventory WHERE product_id = ?")) {
            ps.setLong(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void setThreshold(Connection c, long productId, int threshold) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE inventory SET low_stock_threshold = ? WHERE product_id = ?")) {
            ps.setInt(1, threshold);
            ps.setLong(2, productId);
            ps.executeUpdate();
        }
    }

    public void addMovement(Connection c, long productId, int change, StockReason reason, String reference,
                            String note, Long createdBy) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO stock_movements (product_id, change_qty, resulting_qty, reason, reference, note, created_by) "
                + "VALUES (?, ?, (SELECT quantity FROM inventory WHERE product_id = ?), ?, ?, ?, ?)")) {
            ps.setLong(1, productId);
            ps.setInt(2, change);
            ps.setLong(3, productId);
            ps.setString(4, reason.name());
            ps.setString(5, reference);
            ps.setString(6, note);
            Jdbc.setLong(ps, 7, createdBy);
            ps.executeUpdate();
        }
    }

    public ArrayList<InventoryDto> list(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT p.id, p.sku, p.name, p.active, i.quantity, i.low_stock_threshold FROM inventory i "
                + "JOIN products p ON p.id = i.product_id ORDER BY (i.quantity <= i.low_stock_threshold) DESC, p.name");
             ResultSet rs = ps.executeQuery()) {
            ArrayList<InventoryDto> out = new ArrayList<>();
            while (rs.next()) out.add(map(rs));
            return out;
        }
    }

    public Optional<InventoryDto> find(Connection c, long productId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT p.id, p.sku, p.name, p.active, i.quantity, i.low_stock_threshold FROM inventory i "
                + "JOIN products p ON p.id = i.product_id WHERE p.id = ?")) {
            ps.setLong(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** The newest movements, for one product or for all products when productId is null. */
    public ArrayList<StockMovementDto> movements(Connection c, Long productId, int limit) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT m.id, m.product_id, p.sku, m.change_qty, m.resulting_qty, m.reason, m.reference, m.note, "
                + "u.email AS created_by, m.created_at FROM stock_movements m JOIN products p ON p.id = m.product_id "
                + "LEFT JOIN users u ON u.id = m.created_by WHERE (CAST(? AS BIGINT) IS NULL OR m.product_id = ?) "
                + "ORDER BY m.id DESC LIMIT ?")) {
            Jdbc.setLong(ps, 1, productId);
            Jdbc.setLong(ps, 2, productId);
            ps.setInt(3, limit);
            ArrayList<StockMovementDto> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new StockMovementDto(rs.getLong("id"), rs.getLong("product_id"), rs.getString("sku"),
                            rs.getInt("change_qty"), rs.getInt("resulting_qty"), StockReason.valueOf(rs.getString("reason")),
                            rs.getString("reference"), rs.getString("note"), rs.getString("created_by"),
                            Jdbc.instant(rs, "created_at")));
                }
            }
            return out;
        }
    }

    private InventoryDto map(ResultSet rs) throws SQLException {
        int qty = rs.getInt("quantity");
        int threshold = rs.getInt("low_stock_threshold");
        return new InventoryDto(rs.getLong("id"), rs.getString("sku"), rs.getString("name"), qty, threshold,
                qty <= threshold, rs.getBoolean("active"));
    }
}
