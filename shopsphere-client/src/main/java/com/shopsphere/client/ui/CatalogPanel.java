package com.shopsphere.client.ui;

import com.shopsphere.client.network.ApiClient;
import com.shopsphere.client.util.Async;
import com.shopsphere.client.util.Format;
import com.shopsphere.common.dto.CatalogDtos.CategoryDto;
import com.shopsphere.common.dto.CatalogDtos.ProductDto;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Browse and search products as a grid of cards. */
public class CatalogPanel extends JPanel implements Refreshable {

    private static final long serialVersionUID = 1L;
    private static final CategoryDto ALL = new CategoryDto(null, "All categories", null);

    private final transient ApiClient api;
    private final transient Consumer<String> navigate;
    private final JTextField search = Ui.field(24);
    private final JComboBox<CategoryDto> category = new JComboBox<>();
    private final GridScroll grid = new GridScroll(250);
    private final JLabel status = Ui.muted(" ");
    private boolean categoriesLoaded;

    public CatalogPanel(ApiClient api, Consumer<String> navigate) {
        super(new BorderLayout());
        this.api = api;
        this.navigate = navigate;
        JPanel page = Ui.page("Shop", "Find something you will love");
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        bar.setOpaque(false);
        category.setPreferredSize(new Dimension(200, 36));
        JButton go = Ui.button("Search");
        go.addActionListener(e -> load());
        search.addActionListener(e -> load());
        category.addActionListener(e -> { if (categoriesLoaded) load(); });
        bar.add(search);
        bar.add(category);
        bar.add(go);
        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(bar, BorderLayout.WEST);
        north.add(status, BorderLayout.EAST);
        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(north, BorderLayout.NORTH);
        body.add(grid, BorderLayout.CENTER);
        page.add(body, BorderLayout.CENTER);
        add(page);
    }

    @Override
    public void refresh() {
        Async.run(this, api::categories, cats -> {
            categoriesLoaded = false;
            category.removeAllItems();
            category.addItem(ALL);
            cats.forEach(category::addItem);
            categoriesLoaded = true;
            load();
        });
    }

    private void load() {
        String q = search.getText().trim();
        CategoryDto c = (CategoryDto) category.getSelectedItem();
        Long categoryId = c == null ? null : c.id();
        Async.run(this, () -> api.products(q.isEmpty() ? null : q, categoryId, false), this::show);
    }

    private void show(List<ProductDto> products) {
        List<JPanel> cards = new ArrayList<>();
        for (ProductDto p : products) cards.add(card(p));
        grid.setCards(cards);
        status.setText(products.size() + (products.size() == 1 ? " product" : " products"));
    }

    private JPanel card(ProductDto p) {
        JPanel c = Ui.card();
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        var tile = Ui.imageTile(p.name(), 130);
        tile.setAlignmentX(LEFT_ALIGNMENT);
        c.add(tile);
        c.add(Box.createVerticalStrut(10));
        c.add(left(Ui.label("<html>" + Format.esc(p.name()) + "</html>", Font.BOLD, 14f, Theme.TEXT)));
        c.add(Box.createVerticalStrut(2));
        c.add(left(Ui.muted(p.categoryName() == null ? " " : p.categoryName())));
        c.add(Box.createVerticalStrut(4));
        String rating = p.reviewCount() == 0 ? "No reviews yet" : Format.stars((int) Math.round(p.averageRating())) + " (" + p.reviewCount() + ")";
        c.add(left(Ui.label(rating, Font.PLAIN, 12f, Theme.WARNING)));
        c.add(Box.createVerticalStrut(6));
        c.add(left(Ui.label(Format.money(p.price()), Font.BOLD, 18f, Theme.TEXT)));
        Color stockColor = !p.available() ? Theme.DANGER : p.stock() <= 5 ? Theme.WARNING : Theme.SUCCESS;
        String stockText = !p.available() ? "Out of stock" : p.stock() <= 5 ? "Only " + p.stock() + " left" : "In stock";
        c.add(left(Ui.label(stockText, Font.BOLD, 12f, stockColor)));
        c.add(Box.createVerticalStrut(10));
        JButton add = Ui.button("Add to cart");
        add.setEnabled(p.available());
        add.setAlignmentX(LEFT_ALIGNMENT);
        add.addActionListener(e -> Async.run(this, () -> api.addToCart(p.id(), 1), cart -> {
            status.setText("Added " + p.name() + " to your cart");
        }));
        c.add(add);
        c.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { openDetail(p); }
        });
        return c;
    }

    private void openDetail(ProductDto p) {
        new ProductDialog(javax.swing.SwingUtilities.getWindowAncestor(this), api, p, () -> navigate.accept("Cart")).setVisible(true);
    }

    private static JLabel left(JLabel l) {
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    @SuppressWarnings("unused")
    private static Box spacer() {
        return Box.createHorizontalBox();
    }

    @SuppressWarnings("unused")
    private static javax.swing.border.Border pad() {
        return BorderFactory.createEmptyBorder(4, 4, 4, 4);
    }
}
