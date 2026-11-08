package com.shopsphere.server.dao;

import com.shopsphere.common.dto.CatalogDtos.CategoryDto;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Optional;

public class CategoryDao {

    public ArrayList<CategoryDto> list(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id, name, description FROM categories ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            ArrayList<CategoryDto> out = new ArrayList<>();
            while (rs.next()) out.add(new CategoryDto(rs.getLong("id"), rs.getString("name"), rs.getString("description")));
            return out;
        }
    }

    public Optional<CategoryDto> find(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id, name, description FROM categories WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new CategoryDto(rs.getLong("id"), rs.getString("name"), rs.getString("description")))
                        : Optional.empty();
            }
        }
    }

    public long insert(Connection c, String name, String description) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO categories (name, description) VALUES (?, ?) RETURNING id")) {
            ps.setString(1, name);
            ps.setString(2, description);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    public boolean update(Connection c, long id, String name, String description) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE categories SET name = ?, description = ? WHERE id = ?")) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setLong(3, id);
            return ps.executeUpdate() > 0;
        }
    }

    public int countProducts(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM products WHERE category_id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public boolean delete(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM categories WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }
}
