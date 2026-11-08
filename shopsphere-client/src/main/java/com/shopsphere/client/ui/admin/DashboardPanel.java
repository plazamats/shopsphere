package com.shopsphere.client.ui.admin;

import com.shopsphere.client.network.ApiClient;
import com.shopsphere.client.ui.DataTable;
import com.shopsphere.client.ui.Refreshable;
import com.shopsphere.client.ui.Theme;
import com.shopsphere.client.ui.Ui;
import com.shopsphere.client.util.Async;
import com.shopsphere.client.util.Format;
import com.shopsphere.common.dto.AdminDtos.DashboardDto;
import com.shopsphere.common.dto.AdminDtos.ReportRow;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JPanel;

public class DashboardPanel extends JPanel implements Refreshable {

    private static final long serialVersionUID = 1L;

    private final transient ApiClient api;
    private final JPanel tiles = new JPanel(new GridLayout(1, 5, 14, 0));
    private final DataTable<ReportRow> recent = new DataTable<>(List.of(
            new DataTable.Column<>("Order", r -> r.cells().get(0)),
            new DataTable.Column<>("Customer", r -> r.cells().get(1)),
            new DataTable.Column<>("Status", r -> r.cells().get(2)),
            new DataTable.Column<>("Total", r -> r.cells().get(3))));

    public DashboardPanel(ApiClient api) {
        super(new BorderLayout());
        this.api = api;
        JPanel page = Ui.page("Dashboard", "How the shop is doing right now");
        tiles.setOpaque(false);
        JPanel body = new JPanel(new BorderLayout(0, 18));
        body.setOpaque(false);
        body.add(tiles, BorderLayout.NORTH);
        JPanel lower = new JPanel(new BorderLayout(0, 8));
        lower.setOpaque(false);
        lower.add(Ui.label("Recent orders", Font.BOLD, 16f, Theme.TEXT), BorderLayout.NORTH);
        lower.add(recent, BorderLayout.CENTER);
        body.add(lower, BorderLayout.CENTER);
        page.add(body, BorderLayout.CENTER);
        add(page);
    }

    @Override
    public void refresh() {
        Async.run(this, api::dashboard, this::show);
    }

    private void show(DashboardDto d) {
        tiles.removeAll();
        tiles.add(tile("Revenue", Format.money(d.revenue()), Theme.ACCENT));
        tiles.add(tile("Orders", String.valueOf(d.orders()), Theme.SUCCESS));
        tiles.add(tile("Customers", String.valueOf(d.customers()), Theme.TEXT));
        tiles.add(tile("Products", String.valueOf(d.products()), Theme.TEXT));
        tiles.add(tile("Low stock", String.valueOf(d.lowStockCount()), d.lowStockCount() > 0 ? Theme.DANGER : Theme.SUCCESS));
        tiles.revalidate();
        tiles.repaint();
        recent.setRows(d.recentOrders());
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : d.ordersByStatus().entrySet()) sb.append(e.getKey()).append(": ").append(e.getValue()).append("   ");
        recent.setToolTipText(sb.toString());
    }

    private static JPanel tile(String name, String value, Color color) {
        JPanel c = Ui.card();
        c.setLayout(new BorderLayout(0, 6));
        c.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        c.add(Ui.muted(name), BorderLayout.NORTH);
        c.add(Ui.label(value, Font.BOLD, 26f, color), BorderLayout.CENTER);
        return c;
    }
}
