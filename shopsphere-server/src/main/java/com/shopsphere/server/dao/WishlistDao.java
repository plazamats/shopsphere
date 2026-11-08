package com.shopsphere.server.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WishlistDao {

    public long ensure(Connection c, long userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO wishlists (user_id) VALUES (?) ON CONFLICT (user_id) DO NOTHING")) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM wishlists WHERE user_id = ?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    public List<Long> productIds(Connection c, long wishlistId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT product_id FROM wishlist_items WHERE wishlist_id = ? ORDER BY added_at DESC")) {
            ps.setLong(1, wishlistId);
            List<Long> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getLong(1));
            }
            return out;
        }
    }

    public void add(Connection c, long wishlistId, long productId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO wishlist_items (wishlist_id, product_id) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
            ps.setLong(1, wishlistId);
            ps.setLong(2, productId);
            ps.executeUpdate();
        }
    }

    public void remove(Connection c, long wishlistId, long productId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM wishlist_items WHERE wishlist_id = ? AND product_id = ?")) {
            ps.setLong(1, wishlistId);
            ps.setLong(2, productId);
            ps.executeUpdate();
        }
    }
}
