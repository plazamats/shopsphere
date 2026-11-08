package com.shopsphere.client.ui.admin;

import com.shopsphere.client.network.ApiClient;
import com.shopsphere.client.ui.Theme;
import com.shopsphere.client.ui.Ui;
import com.shopsphere.client.util.Async;
import com.shopsphere.common.dto.CatalogDtos.CategoryDto;
import com.shopsphere.common.dto.CatalogDtos.ProductDto;
import com.shopsphere.common.dto.CatalogDtos.ProductSaveRequest;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.math.BigDecimal;
import java.util.ArrayList;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Create or edit a product. The server validates everything again, this form only collects the input. */
class ProductEditDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final transient ApiClient api;
    private final transient ProductDto existing;
    private final Runnable onSaved;
    private final JTextField sku = Ui.field(20);
    private final JTextField name = Ui.field(20);
    private final JTextField description = Ui.field(20);
    private final JTextField price = Ui.field(20);
    private final JTextField image = Ui.field(20);
    private final JTextField stock = Ui.field(20);
    private final JCheckBox active = new JCheckBox("Active (visible to customers)", true);
    private final JComboBox<CategoryDto> category = new JComboBox<>();
    private final JLabel message = Ui.label(" ", java.awt.Font.PLAIN, 13f, Theme.DANGER);

    ProductEditDialog(Window owner, ApiClient api, ProductDto existing, Runnable onSaved) {
        super(owner, existing == null ? "New product" : "Edit product", ModalityType.APPLICATION_MODAL);
        this.api = api;
        this.existing = existing;
        this.onSaved = onSaved;
        JPanel form = new JPanel(new GridLayout(0, 2, 10, 10));
        form.add(new JLabel("SKU")); form.add(sku);
        form.add(new JLabel("Name")); form.add(name);
        form.add(new JLabel("Description")); form.add(description);
        form.add(new JLabel("Price (R)")); form.add(price);
        form.add(new JLabel("Category")); form.add(category);
        form.add(new JLabel("Image reference")); form.add(image);
        form.add(new JLabel(existing == null ? "Initial stock" : "Stock (use Inventory to change)")); form.add(stock);
        form.add(new JLabel("")); form.add(active);
        JButton save = Ui.button("Save");
        save.addActionListener(e -> save());
        JPanel south = new JPanel(new BorderLayout(10, 0));
        south.add(message, BorderLayout.CENTER);
        south.add(save, BorderLayout.EAST);
        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));
        root.setBackground(Theme.SURFACE);
        form.setOpaque(false);
        root.add(form, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);
        if (existing != null) {
            sku.setText(existing.sku());
            name.setText(existing.name());
            description.setText(existing.description());
            price.setText(existing.price().toPlainString());
            image.setText(existing.imageUrls().isEmpty() ? "" : existing.imageUrls().get(0));
            stock.setText(String.valueOf(existing.stock()));
            stock.setEnabled(false);
            active.setSelected(existing.active());
        } else {
            stock.setText("0");
        }
        setSize(520, 470);
        setLocationRelativeTo(owner);
        Async.run(this, api::categories, list -> {
            list.forEach(category::addItem);
            if (existing != null) {
                for (int i = 0; i < category.getItemCount(); i++) {
                    if (category.getItemAt(i).id().equals(existing.categoryId())) category.setSelectedIndex(i);
                }
            }
        });
    }

    private void save() {
        BigDecimal p;
        int initial;
        try {
            p = new BigDecimal(price.getText().trim());
            initial = Integer.parseInt(stock.getText().trim());
        } catch (NumberFormatException e) {
            message.setText("Price and stock must be numbers");
            return;
        }
        CategoryDto c = (CategoryDto) category.getSelectedItem();
        ArrayList<String> images = new ArrayList<>();
        if (!image.getText().isBlank()) images.add(image.getText().trim());
        ProductSaveRequest r = new ProductSaveRequest(existing == null ? null : existing.id(), sku.getText().trim(),
                name.getText().trim(), description.getText().trim(), p, c == null ? null : c.id(), active.isSelected(),
                images, initial);
        Async.run(this, () -> api.saveProduct(r), saved -> {
            dispose();
            onSaved.run();
        }, err -> message.setText("<html>" + com.shopsphere.client.util.Format.esc(err.getMessage()) + "</html>"));
    }
}
