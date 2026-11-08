package com.shopsphere.common.dto;

import com.shopsphere.common.protocol.StockReason;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/** DTOs for inventory, dashboards, reports, audit logs and settings. */
public final class AdminDtos {

    private AdminDtos() { }

    public record InventoryDto(long productId, String sku, String name, int quantity, int lowStockThreshold,
                               boolean lowStock, boolean productActive) implements Serializable { }

    /** delta is positive to add stock and negative to remove it. */
    public record StockAdjustRequest(long productId, int delta, StockReason reason, String note)
            implements Serializable { }

    public record StockMovementDto(long id, long productId, String sku, int change, int resultingQuantity,
                                   StockReason reason, String reference, String note, String createdBy,
                                   Instant createdAt) implements Serializable { }

    public record DashboardDto(int customers, int products, int orders, BigDecimal revenue, int lowStockCount,
                               LinkedHashMap<String, Integer> ordersByStatus, ArrayList<ReportRow> recentOrders)
            implements Serializable { }

    /** A row of text cells, used by reports and the dashboard. */
    public record ReportRow(ArrayList<String> cells) implements Serializable { }

    public record ReportDto(String title, ArrayList<String> columns, ArrayList<ReportRow> rows)
            implements Serializable { }

    public record AuditLogDto(long id, String actorEmail, String action, String category, String entityType,
                              String entityId, String details, Instant createdAt) implements Serializable { }

    public record AuditQuery(int limit) implements Serializable { }

    public record SettingDto(String key, String value, String description) implements Serializable { }
}
