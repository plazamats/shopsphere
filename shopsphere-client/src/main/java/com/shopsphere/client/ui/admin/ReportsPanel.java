package com.shopsphere.client.ui.admin;

import com.shopsphere.client.network.ApiClient;
import com.shopsphere.client.ui.Refreshable;
import com.shopsphere.client.ui.Theme;
import com.shopsphere.client.ui.Ui;
import com.shopsphere.client.util.Async;
import com.shopsphere.common.dto.AdminDtos.ReportDto;
import com.shopsphere.common.dto.AdminDtos.ReportRow;
import com.shopsphere.common.protocol.ReportType;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

public class ReportsPanel extends JPanel implements Refreshable {

    private static final long serialVersionUID = 1L;

    private final transient ApiClient api;
    private final JComboBox<ReportType> type = new JComboBox<>(ReportType.values());
    private final JTable table = new JTable();
    private final JLabel heading = Ui.label(" ", Font.BOLD, 16f, Theme.TEXT);

    public ReportsPanel(ApiClient api) {
        super(new BorderLayout());
        this.api = api;
        JPanel page = Ui.page("Reports", "Sales and stock reports calculated on the server");
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.setOpaque(false);
        JButton run = Ui.button("Run report");
        run.addActionListener(e -> refresh());
        bar.add(type);
        bar.add(run);
        table.setRowHeight(32);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);
        JPanel head = new JPanel(new BorderLayout(0, 8));
        head.setOpaque(false);
        head.add(bar, BorderLayout.NORTH);
        head.add(heading, BorderLayout.SOUTH);
        body.add(head, BorderLayout.NORTH);
        body.add(scroll, BorderLayout.CENTER);
        page.add(body, BorderLayout.CENTER);
        add(page);
    }

    @Override
    public void refresh() {
        ReportType t = (ReportType) type.getSelectedItem();
        Async.run(this, () -> api.report(t), this::show);
    }

    private void show(ReportDto r) {
        heading.setText(r.title());
        DefaultTableModel model = new DefaultTableModel(r.columns().toArray(), 0) {
            private static final long serialVersionUID = 1L;
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        for (ReportRow row : r.rows()) model.addRow(row.cells().toArray());
        table.setModel(model);
    }
}
