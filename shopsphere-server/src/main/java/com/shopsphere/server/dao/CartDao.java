package com.shopsphere.server.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CartDao {

    /** One cart line with the CURRENT product price and stock. The cart never stores a price. */
    public record CartRow(long productId, String sku, String name, BigDecimal unitPrice, int quantity,
                          boolean active, int stock) { }

    public long ensureCart(Connection c, long userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO carts (user_id) VALUES (?) ON CONFLICT (user_id) DO NOTHING")) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM carts WHERE user_id = ?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** Lock the cart so two checkouts by the same customer run one after the other. */
    public long lockCart(Connection c, long userId) throws SQLException {
        long id = ensureCart(c, userId);
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM carts WHERE id = ? FOR UPDATE")) {
            ps.setLong(1, id);
            ps.executeQuery().close();
        }
        return id;
    }

    public List<CartRow> items(Connection c, long cartId) throws SQLException {
        return items(c, cartId, false);
    }

    /** With lockProducts the product rows are share-locked, so a price cannot change during checkout. */
    public List<CartRow> items(Connection c, long cartId, boolean lockProducts) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT p.id, p.sku, p.name, p.price, ci.quantity, p.active, COALESCE(i.quantity, 0) AS stock "
                + "FROM cart_items ci JOIN products p ON p.id = ci.product_id "
                + "LEFT JOIN inventory i ON i.product_id = p.id WHERE ci.cart_id = ? ORDER BY ci.added_at, p.id"
                + (lockProducts ? " FOR SHARE OF p" : ""))) {
            ps.setLong(1, cartId);
            List<CartRow> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new CartRow(rs.getLong("id"), rs.getString("sku"), rs.getString("name"),
                            rs.getBigDecimal("price"), rs.getInt("quantity"), rs.getBoolean("active"), rs.getInt("stock")));
                }
            }
            return out;
        }
    }

    public int quantityOf(Connection c, long cartId, long productId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT quantity FROM cart_items WHERE cart_id = ? AND product_id = ?")) {
            ps.setLong(1, cartId);
            ps.setLong(2, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void setQuantity(Connection c, long cartId, long productId, int quantity) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO cart_items (cart_id, product_id, quantity) VALUES (?, ?, ?) "
                + "ON CONFLICT (cart_id, product_id) DO UPDATE SET quantity = EXCLUDED.quantity")) {
            ps.setLong(1, cartId);
            ps.setLong(2, productId);
            ps.setInt(3, quantity);
            ps.executeUpdate();
        }
        touch(c, cartId);
    }

    public void remove(Connection c, long cartId, long productId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM cart_items WHERE cart_id = ? AND product_id = ?")) {
            ps.setLong(1, cartId);
            ps.setLong(2, productId);
            ps.executeUpdate();
        }
        touch(c, cartId);
    }

    public void clear(Connection c, long cartId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM cart_items WHERE cart_id = ?")) {
            ps.setLong(1, cartId);
            ps.executeUpdate();
        }
        touch(c, cartId);
    }

    private void touch(Connection c, long cartId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE carts SET updated_at = now() WHERE id = ?")) {
            ps.setLong(1, cartId);
            ps.executeUpdate();
        }
    }
}
