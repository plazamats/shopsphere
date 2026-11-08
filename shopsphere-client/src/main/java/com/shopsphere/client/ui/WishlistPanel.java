package com.shopsphere.client.ui;

import com.shopsphere.client.network.ApiClient;
import com.shopsphere.client.util.Async;
import com.shopsphere.client.util.Format;
import com.shopsphere.common.dto.CatalogDtos.ProductDto;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JPanel;

public class WishlistPanel extends JPanel implements Refreshable {

    private static final long serialVersionUID = 1L;

    private final transient ApiClient api;
    private final DataTable<ProductDto> table = new DataTable<>(List.of(
            new DataTable.Column<>("Product", ProductDto::name),
            new DataTable.Column<>("Category", ProductDto::categoryName),
            new DataTable.Column<>("Price", p -> Format.money(p.price())),
            new DataTable.Column<>("Availability", p -> p.available() ? "In stock" : "Out of stock")));

    public WishlistPanel(ApiClient api) {
        super(new BorderLayout());
        this.api = api;
        JPanel page = Ui.page("Wishlist", "Products you saved for later");
        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(table, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        JButton addCart = Ui.button("Add to cart");
        addCart.addActionListener(e -> {
            ProductDto p = table.selected();
            if (p != null) Async.run(this, () -> api.addToCart(p.id(), 1), c -> Async.showInfo(this, p.name() + " was added to your cart"));
        });
        JButton remove = Ui.button("Remove", Ui.Kind.SECONDARY);
        remove.addActionListener(e -> {
            ProductDto p = table.selected();
            if (p != null) Async.run(this, () -> api.removeWishlist(p.id()), table::setRows);
        });
        actions.add(addCart);
        actions.add(remove);
        body.add(actions, BorderLayout.SOUTH);
        page.add(body, BorderLayout.CENTER);
        add(page);
    }

    @Override
    public void refresh() {
        Async.run(this, api::wishlist, table::setRows);
    }
}
