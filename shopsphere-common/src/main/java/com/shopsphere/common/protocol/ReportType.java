package com.shopsphere.common.protocol;

public enum ReportType {
    SALES_BY_DAY("Sales by day (last 30 days)"),
    TOP_PRODUCTS("Top selling products"),
    SALES_BY_CATEGORY("Sales by category"),
    ORDER_STATUS_SUMMARY("Orders by status"),
    LOW_STOCK("Low stock products");

    private final String title;

    ReportType(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
