package com.shopsphere.server.dao;

import com.shopsphere.common.dto.CatalogDtos.ProductDto;
import com.shopsphere.common.dto.CatalogDtos.ProductSaveRequest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ProductDao {

    private static final String SELECT =
            "SELECT p.id, p.sku, p.name, p.description, p.price, p.category_id, c.name AS category_name, p.active, "
            + "COALESCE(i.quantity, 0) AS stock, "
            + "COALESCE((SELECT AVG(r.rating) FROM reviews r WHERE r.product_id = p.id), 0) AS avg_rating, "
            + "(SELECT COUNT(*) FROM reviews r WHERE r.product_id = p.id) AS review_count "
            + "FROM products p LEFT JOIN categories c ON c.id = p.category_id "
            + "LEFT JOIN inventory i ON i.product_id = p.id ";

    /** Search by name, SKU or description, optionally in one category. All values are bound parameters. */
    public ArrayList<ProductDto> search(Connection c, String search, Long categoryId, boolean includeInactive)
            throws SQLException {
        String sql = SELECT
                + "WHERE (CAST(? AS VARCHAR) IS NULL OR lower(p.name) LIKE ? ESCAPE '\\' OR lower(p.sku) LIKE ? ESCAPE '\\' "
                + "OR lower(COALESCE(p.description, '')) LIKE ? ESCAPE '\\') "
                + "AND (CAST(? AS BIGINT) IS NULL OR p.category_id = ?) AND (? OR p.active) ORDER BY p.name, p.id";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            String pattern = search == null ? null : Jdbc.contains(search);
            ps.setString(1, search);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            ps.setString(4, pattern);
            Jdbc.setLong(ps, 5, categoryId);
            Jdbc.setLong(ps, 6, categoryId);
            ps.setBoolean(7, includeInactive);
            return readAll(c, ps);
        }
    }

    public Optional<ProductDto> find(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + "WHERE p.id = ?")) {
            ps.setLong(1, id);
            ArrayList<ProductDto> list = readAll(c, ps);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        }
    }

    /** The products with the given ids, for the cart and the wishlist. */
    public ArrayList<ProductDto> findAll(Connection c, List<Long> ids) throws SQLException {
        if (ids.isEmpty()) return new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(SELECT + "WHERE p.id = ANY (?) ORDER BY p.name, p.id")) {
            ps.setArray(1, c.createArrayOf("bigint", ids.toArray(new Long[0])));
            return readAll(c, ps);
        }
    }

    public boolean skuExists(Connection c, String sku, Long exceptId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM products WHERE upper(sku) = upper(?) AND (CAST(? AS BIGINT) IS NULL OR id <> ?)")) {
            ps.setString(1, sku);
            Jdbc.setLong(ps, 2, exceptId);
            Jdbc.setLong(ps, 3, exceptId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public long insert(Connection c, ProductSaveRequest r) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO products (sku, name, description, price, category_id, active) VALUES (?, ?, ?, ?, ?, ?) RETURNING id")) {
            ps.setString(1, r.sku());
            ps.setString(2, r.name());
            ps.setString(3, r.description());
            ps.setBigDecimal(4, r.price());
            Jdbc.setLong(ps, 5, r.categoryId());
            ps.setBoolean(6, r.active());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    public boolean update(Connection c, ProductSaveRequest r) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE products SET sku = ?, name = ?, description = ?, price = ?, category_id = ?, active = ?, "
                + "updated_at = now() WHERE id = ?")) {
            ps.setString(1, r.sku());
            ps.setString(2, r.name());
            ps.setString(3, r.description());
            ps.setBigDecimal(4, r.price());
            Jdbc.setLong(ps, 5, r.categoryId());
            ps.setBoolean(6, r.active());
            ps.setLong(7, r.id());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean setActive(Connection c, long id, boolean active) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE products SET active = ?, updated_at = now() WHERE id = ?")) {
            ps.setBoolean(1, active);
            ps.setLong(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    public void replaceImages(Connection c, long productId, List<String> urls) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM product_images WHERE product_id = ?")) {
            ps.setLong(1, productId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO product_images (product_id, image_url, sort_order) VALUES (?, ?, ?)")) {
            int order = 0;
            for (String url : urls) {
                ps.setLong(1, productId);
                ps.setString(2, url);
                ps.setInt(3, order++);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private ArrayList<ProductDto> readAll(Connection c, PreparedStatement ps) throws SQLException {
        List<Object[]> rows = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new Object[]{rs.getLong("id"), rs.getString("sku"), rs.getString("name"),
                        rs.getString("description"), rs.getBigDecimal("price"), Jdbc.getLong(rs, "category_id"),
                        rs.getString("category_name"), rs.getBoolean("active"), rs.getInt("stock"),
                        rs.getDouble("avg_rating"), rs.getInt("review_count")});
            }
        }
        Map<Long, ArrayList<String>> images = images(c, rows);
        ArrayList<ProductDto> out = new ArrayList<>();
        for (Object[] r : rows) {
            long id = (Long) r[0];
            int stock = (Integer) r[8];
            boolean active = (Boolean) r[7];
            double avg = Math.round((Double) r[9] * 10.0) / 10.0;
            out.add(new ProductDto(id, (String) r[1], (String) r[2], (String) r[3], (BigDecimal) r[4], (Long) r[5],
                    (String) r[6], active, stock, active && stock > 0,
                    images.getOrDefault(id, new ArrayList<>()), avg, (Integer) r[10]));
        }
        return out;
    }

    private Map<Long, ArrayList<String>> images(Connection c, List<Object[]> rows) throws SQLException {
        Map<Long, ArrayList<String>> map = new HashMap<>();
        if (rows.isEmpty()) return map;
        Long[] ids = rows.stream().map(r -> (Long) r[0]).toArray(Long[]::new);
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT product_id, image_url FROM product_images WHERE product_id = ANY (?) ORDER BY product_id, sort_order, id")) {
            ps.setArray(1, c.createArrayOf("bigint", ids));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getString(2));
                }
            }
        }
        return map;
    }
}
