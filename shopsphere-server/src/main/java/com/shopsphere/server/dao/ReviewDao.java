package com.shopsphere.server.dao;

import com.shopsphere.common.dto.CatalogDtos.ReviewDto;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class ReviewDao {

    public ArrayList<ReviewDto> list(Connection c, long productId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT r.id, r.product_id, u.first_name || ' ' || left(u.last_name, 1) || '.' AS reviewer, r.rating, "
                + "r.comment, r.verified_purchase, r.created_at FROM reviews r JOIN users u ON u.id = r.user_id "
                + "WHERE r.product_id = ? ORDER BY r.created_at DESC, r.id DESC")) {
            ps.setLong(1, productId);
            ArrayList<ReviewDto> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ReviewDto(rs.getLong("id"), rs.getLong("product_id"), rs.getString("reviewer"),
                            rs.getInt("rating"), rs.getString("comment"), rs.getBoolean("verified_purchase"),
                            Jdbc.instant(rs, "created_at")));
                }
            }
            return out;
        }
    }

    public boolean exists(Connection c, long userId, long productId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM reviews WHERE user_id = ? AND product_id = ?")) {
            ps.setLong(1, userId);
            ps.setLong(2, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public long insert(Connection c, long userId, long productId, int rating, String comment, boolean verified)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO reviews (product_id, user_id, rating, comment, verified_purchase) VALUES (?, ?, ?, ?, ?) RETURNING id")) {
            ps.setLong(1, productId);
            ps.setLong(2, userId);
            ps.setInt(3, rating);
            ps.setString(4, comment);
            ps.setBoolean(5, verified);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
