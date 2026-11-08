package com.shopsphere.client.ui;

import com.shopsphere.client.network.ApiClient;
import com.shopsphere.client.util.Async;
import com.shopsphere.client.util.Format;
import com.shopsphere.common.dto.ShoppingDtos.OrderDto;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/** Order history with status tracking, cancellation and receipts. */
public class OrdersPanel extends JPanel implements Refreshable {

    private static final long serialVersionUID = 1L;

    private final transient ApiClient api;
    private final DataTable<OrderDto> table = new DataTable<>(List.of(
            new DataTable.Column<>("Order", OrderDto::orderNumber),
            new DataTable.Column<>("Date", o -> Format.date(o.createdAt())),
            new DataTable.Column<>("Status", o -> Format.nice(o.status())),
            new DataTable.Column<>("Total", o -> Format.money(o.pricing().total()))));

    public OrdersPanel(ApiClient api) {
        super(new BorderLayout());
        this.api = api;
        JPanel page = Ui.page("My orders", "Track, cancel and view receipts");
        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(table, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        JButton details = Ui.button("View receipt");
        details.addActionListener(e -> receipt());
        JButton cancel = Ui.button("Cancel order", Ui.Kind.DANGER);
        cancel.addActionListener(e -> cancel());
        JButton reload = Ui.button("Refresh", Ui.Kind.SECONDARY);
        reload.addActionListener(e -> refresh());
        actions.add(details);
        actions.add(cancel);
        actions.add(reload);
        body.add(actions, BorderLayout.SOUTH);
        page.add(body, BorderLayout.CENTER);
        add(page);
    }

    @Override
    public void refresh() {
        Async.run(this, api::myOrders, table::setRows);
    }

    private void receipt() {
        OrderDto o = table.selected();
        if (o == null) return;
        Async.run(this, () -> api.receipt(o.id()), text -> {
            JTextArea area = new JTextArea(text, 22, 64);
            area.setEditable(false);
            area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
            JOptionPane.showMessageDialog(this, new JScrollPane(area), "Receipt " + o.orderNumber(), JOptionPane.PLAIN_MESSAGE);
        });
    }

    private void cancel() {
        OrderDto o = table.selected();
        if (o == null) return;
        int ok = JOptionPane.showConfirmDialog(this, "Cancel order " + o.orderNumber() + "? The simulated payment will be refunded.",
                "Cancel order", JOptionPane.YES_NO_OPTION);
        if (ok == JOptionPane.YES_OPTION) {
            Async.run(this, () -> api.cancelOrder(o.id()), r -> refresh());
        }
    }
}
