package com.shopsphere.client.ui.admin;

import com.shopsphere.client.network.ApiClient;
import com.shopsphere.client.ui.DataTable;
import com.shopsphere.client.ui.Ui;
import com.shopsphere.client.util.Format;
import com.shopsphere.common.dto.CatalogDtos.ProductDto;
import java.util.List;
import javax.swing.JOptionPane;

public class ProductsAdminPanel extends TablePage<ProductDto> {

    private static final long serialVersionUID = 1L;

    private final transient ApiClient api;

    public ProductsAdminPanel(ApiClient api) {
        super("Products", "Create, edit and archive products. Archived products stay in old orders", List.of(
                new DataTable.Column<>("SKU", ProductDto::sku),
                new DataTable.Column<>("Name", ProductDto::name),
                new DataTable.Column<>("Category", ProductDto::categoryName),
                new DataTable.Column<>("Price", p -> Format.money(p.price())),
                new DataTable.Column<>("Stock", ProductDto::stock),
                new DataTable.Column<>("Status", p -> p.active() ? "Active" : "Archived")));
        this.api = api;
        action("New product", Ui.Kind.PRIMARY, () -> edit(null));
        onSelected("Edit", Ui.Kind.SECONDARY, this::edit);
        onSelected("Archive", Ui.Kind.DANGER, p -> {
            int ok = JOptionPane.showConfirmDialog(this, "Archive " + p.name() + "? Customers will no longer see it.", "Archive", JOptionPane.YES_NO_OPTION);
            if (ok == JOptionPane.YES_OPTION) call(() -> api.archiveProduct(p.id()), r -> refresh());
        });
    }

    private void edit(ProductDto p) {
        new ProductEditDialog(javax.swing.SwingUtilities.getWindowAncestor(this), api, p, this::refresh).setVisible(true);
    }

    @Override
    public void refresh() {
        load(() -> api.products(null, null, true));
    }
}
