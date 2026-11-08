package com.shopsphere.client.ui.admin;

import com.shopsphere.client.network.ApiClient;
import com.shopsphere.client.ui.DataTable;
import com.shopsphere.client.ui.Ui;
import com.shopsphere.common.dto.CatalogDtos.CategoryDto;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class CategoriesPanel extends TablePage<CategoryDto> {

    private static final long serialVersionUID = 1L;

    private final transient ApiClient api;

    public CategoriesPanel(ApiClient api) {
        super("Categories", "Group products so customers can find them", List.of(
                new DataTable.Column<>("Name", CategoryDto::name),
                new DataTable.Column<>("Description", CategoryDto::description)));
        this.api = api;
        action("New category", Ui.Kind.PRIMARY, () -> edit(null));
        onSelected("Edit", Ui.Kind.SECONDARY, this::edit);
        onSelected("Delete", Ui.Kind.DANGER, c -> {
            int ok = JOptionPane.showConfirmDialog(this, "Delete " + c.name() + "?", "Delete", JOptionPane.YES_NO_OPTION);
            if (ok == JOptionPane.YES_OPTION) call(() -> api.deleteCategory(c.id()), r -> refresh());
        });
    }

    private void edit(CategoryDto c) {
        JTextField name = Ui.field(20);
        JTextField desc = Ui.field(20);
        if (c != null) {
            name.setText(c.name());
            desc.setText(c.description());
        }
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Name")); form.add(name);
        form.add(new JLabel("Description")); form.add(desc);
        int ok = JOptionPane.showConfirmDialog(this, form, c == null ? "New category" : "Edit category", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok == JOptionPane.OK_OPTION) {
            call(() -> api.saveCategory(new CategoryDto(c == null ? null : c.id(), name.getText().trim(), desc.getText().trim())), r -> refresh());
        }
    }

    @Override
    public void refresh() {
        load(api::categories);
    }
}
