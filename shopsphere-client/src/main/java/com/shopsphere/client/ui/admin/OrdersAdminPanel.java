package com.shopsphere.client.ui.admin;

import com.shopsphere.client.network.ApiClient;
import com.shopsphere.client.ui.DataTable;
import com.shopsphere.client.ui.Ui;
import com.shopsphere.client.util.Format;
import com.shopsphere.common.dto.ShoppingDtos.OrderDto;
import com.shopsphere.common.protocol.OrderStatus;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

public class OrdersAdminPanel extends TablePage<OrderDto> {

    private static final long serialVersionUID = 1L;

    private final transient ApiClient api;
    private final JTextField search = Ui.field(18);
    private final JComboBox<Object> filter = new JComboBox<>();

    public OrdersAdminPanel(ApiClient api) {
        super("Orders", "Follow orders from payment to delivery", List.of(
                new DataTable.Column<>("Order", OrderDto::orderNumber),
                new DataTable.Column<>("Customer", OrderDto::customerEmail),
                new DataTable.Column<>("Date", o -> Format.date(o.createdAt())),
                new DataTable.Column<>("Status", o -> Format.nice(o.status())),
                new DataTable.Column<>("Total", o -> Format.money(o.pricing().total()))));
        this.api = api;
        filter.addItem("All statuses");
        for (OrderStatus s : OrderStatus.values()) filter.addItem(s);
        JButton go = Ui.button("Search");
        go.addActionListener(e -> refresh());
        addTop(search);
        addTop(filter);
        addTop(go);
        onSelected("Change status", Ui.Kind.PRIMARY, this::changeStatus);
        onSelected("View receipt", Ui.Kind.SECONDARY, o -> call(() -> api.receipt(o.id()), text -> {
            javax.swing.JTextArea area = new javax.swing.JTextArea(text, 22, 64);
            area.setEditable(false);
            area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
            JOptionPane.showMessageDialog(this, new javax.swing.JScrollPane(area), o.orderNumber(), JOptionPane.PLAIN_MESSAGE);
        }));
    }

    private void changeStatus(OrderDto o) {
        Object[] options = o.status().allowedNext().toArray();
        if (options.length == 0) {
            JOptionPane.showMessageDialog(this, "This order is " + Format.nice(o.status()) + " and cannot change any more.");
            return;
        }
        Object choice = JOptionPane.showInputDialog(this, "Move " + o.orderNumber() + " to:", "Change status",
                JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (choice != null) call(() -> api.setOrderStatus(o.id(), (OrderStatus) choice), r -> refresh());
    }

    @Override
    public void refresh() {
        String q = search.getText().trim();
        Object f = filter.getSelectedItem();
        OrderStatus status = f instanceof OrderStatus s ? s : null;
        load(() -> api.allOrders(status, q.isEmpty() ? null : q));
    }
}
