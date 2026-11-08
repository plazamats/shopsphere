package com.shopsphere.server.dao;

import com.shopsphere.common.dto.AdminDtos.DashboardDto;
import com.shopsphere.common.dto.AdminDtos.ReportDto;
import com.shopsphere.common.dto.AdminDtos.ReportRow;
import com.shopsphere.common.protocol.ReportType;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Read-only queries for the dashboard and the reports. */
public class ReportDao {

    /** Orders that count as revenue. Cancelled and failed orders do not. */
    private static final String REVENUE_STATUSES = "('PAID','PROCESSING','SHIPPED','DELIVERED')";

    public DashboardDto dashboard(Connection c) throws SQLException {
        int customers = scalarInt(c, "SELECT count(*) FROM user_roles WHERE role_id = 1");
        int products = scalarInt(c, "SELECT count(*) FROM products WHERE active");
        int orders = scalarInt(c, "SELECT count(*) FROM orders");
        int lowStock = scalarInt(c, "SELECT count(*) FROM inventory i JOIN products p ON p.id = i.product_id "
                + "WHERE p.active AND i.quantity <= i.low_stock_threshold");
        BigDecimal revenue;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(sum(total), 0) FROM orders WHERE status IN " + REVENUE_STATUSES);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            revenue = rs.getBigDecimal(1).setScale(2);
        }
        LinkedHashMap<String, Integer> byStatus = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT status, count(*) FROM orders GROUP BY status ORDER BY status");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) byStatus.put(rs.getString(1), rs.getInt(2));
        }
        ArrayList<ReportRow> recent = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT o.order_number, u.email, o.status, o.total, to_char(o.created_at, 'YYYY-MM-DD HH24:MI') "
                + "FROM orders o JOIN users u ON u.id = o.user_id ORDER BY o.id DESC LIMIT 5");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) recent.add(row(rs, 5));
        }
        return new DashboardDto(customers, products, orders, revenue, lowStock, byStatus, recent);
    }

    public ReportDto report(Connection c, ReportType type) throws SQLException {
        String sql;
        switch (type) {
            case SALES_BY_DAY:
                sql = "SELECT to_char(created_at::date, 'YYYY-MM-DD') AS day, count(*) AS orders, sum(total) AS revenue "
                    + "FROM orders WHERE status IN " + REVENUE_STATUSES + " AND created_at >= now() - interval '30 days' "
                    + "GROUP BY created_at::date ORDER BY created_at::date DESC";
                break;
            case TOP_PRODUCTS:
                sql = "SELECT oi.sku, oi.product_name AS product, sum(oi.quantity) AS units_sold, sum(oi.line_total) AS revenue "
                    + "FROM order_items oi JOIN orders o ON o.id = oi.order_id WHERE o.status IN " + REVENUE_STATUSES
                    + " GROUP BY oi.sku, oi.product_name ORDER BY units_sold DESC, revenue DESC LIMIT 10";
                break;
            case SALES_BY_CATEGORY:
                sql = "SELECT COALESCE(cat.name, 'Uncategorised') AS category, sum(oi.quantity) AS units_sold, "
                    + "sum(oi.line_total) AS revenue FROM order_items oi JOIN orders o ON o.id = oi.order_id "
                    + "JOIN products p ON p.id = oi.product_id LEFT JOIN categories cat ON cat.id = p.category_id "
                    + "WHERE o.status IN " + REVENUE_STATUSES + " GROUP BY cat.name ORDER BY revenue DESC";
                break;
            case ORDER_STATUS_SUMMARY:
                sql = "SELECT status, count(*) AS orders, sum(total) AS total_value FROM orders GROUP BY status ORDER BY status";
                break;
            case LOW_STOCK:
                sql = "SELECT p.sku, p.name AS product, i.quantity AS in_stock, i.low_stock_threshold AS threshold "
                    + "FROM inventory i JOIN products p ON p.id = i.product_id "
                    + "WHERE p.active AND i.quantity <= i.low_stock_threshold ORDER BY i.quantity, p.name";
                break;
            default:
                throw new IllegalArgumentException("Unknown report " + type);
        }
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData md = rs.getMetaData();
            ArrayList<String> columns = new ArrayList<>();
            for (int i = 1; i <= md.getColumnCount(); i++) columns.add(md.getColumnLabel(i));
            ArrayList<ReportRow> rows = new ArrayList<>();
            while (rs.next()) rows.add(row(rs, md.getColumnCount()));
            return new ReportDto(type.title(), columns, rows);
        }
    }

    private int scalarInt(Connection c, String sql) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private ReportRow row(ResultSet rs, int columns) throws SQLException {
        List<String> cells = new ArrayList<>();
        for (int i = 1; i <= columns; i++) {
            Object v = rs.getObject(i);
            cells.add(v == null ? "" : v.toString());
        }
        return new ReportRow(new ArrayList<>(cells));
    }
}
