package com.shopsphere.client.ui.admin;

import com.shopsphere.client.network.ApiClient;
import com.shopsphere.client.ui.DataTable;
import com.shopsphere.client.ui.Ui;
import com.shopsphere.client.util.Format;
import com.shopsphere.common.dto.AdminDtos.InventoryDto;
import com.shopsphere.common.dto.AdminDtos.StockAdjustRequest;
import com.shopsphere.common.dto.AdminDtos.StockMovementDto;
import com.shopsphere.common.protocol.StockReason;
import java.awt.GridLayout;
import java.util.Arrays;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

public class InventoryPanel extends TablePage<InventoryDto> {

    private static final long serialVersionUID = 1L;

    private final transient ApiClient api;

    public InventoryPanel(ApiClient api) {
        super("Inventory", "Stock levels, adjustments and low stock alerts", List.of(
                new DataTable.Column<>("SKU", InventoryDto::sku),
                new DataTable.Column<>("Product", InventoryDto::name),
                new DataTable.Column<>("In stock", InventoryDto::quantity),
                new DataTable.Column<>("Alert below", InventoryDto::lowStockThreshold),
                new DataTable.Column<>("Alert", i -> i.lowStock() ? "LOW STOCK" : "")));
        this.api = api;
        onSelected("Adjust stock", Ui.Kind.PRIMARY, this::adjust);
        onSelected("Movement history", Ui.Kind.SECONDARY, this::history);
    }

    private void adjust(InventoryDto i) {
        JTextField delta = Ui.field(10);
        JTextField note = Ui.field(10);
        JComboBox<StockReason> reason = new JComboBox<>();
        Arrays.stream(StockReason.values()).filter(StockReason::isManual).forEach(reason::addItem);
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Change (+ add, - remove)")); form.add(delta);
        form.add(new JLabel("Reason")); form.add(reason);
        form.add(new JLabel("Note")); form.add(note);
        int ok = JOptionPane.showConfirmDialog(this, form, "Adjust " + i.name() + " (now " + i.quantity() + ")",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;
        int d;
        try {
            d = Integer.parseInt(delta.getText().trim().replace("+", ""));
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "The change must be a whole number");
            return;
        }
        call(() -> api.adjustStock(new StockAdjustRequest(i.productId(), d, (StockReason) reason.getSelectedItem(), note.getText())),
                r -> refresh());
    }

    private void history(InventoryDto i) {
        call(() -> api.movements(i.productId()), list -> {
            DataTable<StockMovementDto> t = new DataTable<>(List.of(
                    new DataTable.Column<>("When", m -> Format.date(m.createdAt())),
                    new DataTable.Column<>("Change", StockMovementDto::change),
                    new DataTable.Column<>("Result", StockMovementDto::resultingQuantity),
                    new DataTable.Column<>("Reason", m -> Format.nice(m.reason())),
                    new DataTable.Column<>("Reference", StockMovementDto::reference),
                    new DataTable.Column<>("By", StockMovementDto::createdBy)));
            t.setRows(list);
            JScrollPane wrap = new JScrollPane(t);
            wrap.setPreferredSize(new java.awt.Dimension(720, 320));
            JOptionPane.showMessageDialog(this, wrap, "Stock history: " + i.name(), JOptionPane.PLAIN_MESSAGE);
        });
    }

    @Override
    public void refresh() {
        load(api::inventory);
    }
}
