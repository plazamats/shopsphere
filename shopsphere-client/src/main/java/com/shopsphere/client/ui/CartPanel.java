package com.shopsphere.client.ui;

import com.shopsphere.client.network.ApiClient;
import com.shopsphere.client.util.Async;
import com.shopsphere.client.util.Format;
import com.shopsphere.common.dto.ShoppingDtos.CartDto;
import com.shopsphere.common.dto.ShoppingDtos.CartItemDto;
import com.shopsphere.common.dto.ShoppingDtos.PriceBreakdown;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

/** The cart. The server calculates every price and total, this screen only shows them. */
public class CartPanel extends JPanel implements Refreshable {

    private static final long serialVersionUID = 1L;

    private final transient ApiClient api;
    private final transient Consumer<String> navigate;
    private final DataTable<CartItemDto> table = new DataTable<>(List.of(
            new DataTable.Column<>("Product", CartItemDto::name),
            new DataTable.Column<>("Price", i -> Format.money(i.unitPrice())),
            new DataTable.Column<>("Qty", CartItemDto::quantity),
            new DataTable.Column<>("Line total", i -> Format.money(i.lineTotal())),
            new DataTable.Column<>("Status", i -> i.purchasable() ? "OK" : "Not enough stock (" + i.stockAvailable() + " left)")));
    private final JPanel totals = Ui.card();
    private final JButton checkout = Ui.button("Checkout");
    private transient CartDto cart;

    public CartPanel(ApiClient api, Consumer<String> navigate) {
        super(new BorderLayout());
        this.api = api;
        this.navigate = navigate;
        JPanel page = Ui.page("Your cart", "Prices and totals are calculated by the server");
        JPanel body = new JPanel(new BorderLayout(16, 0));
        body.setOpaque(false);
        JPanel left = new JPanel(new BorderLayout(0, 10));
        left.setOpaque(false);
        left.add(table, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        JButton change = Ui.button("Change quantity", Ui.Kind.SECONDARY);
        change.addActionListener(e -> changeQuantity());
        JButton remove = Ui.button("Remove", Ui.Kind.SECONDARY);
        remove.addActionListener(e -> {
            CartItemDto i = table.selected();
            if (i != null) Async.run(this, () -> api.removeFromCart(i.productId()), this::show);
        });
        JButton keep = Ui.button("Continue shopping", Ui.Kind.SECONDARY);
        keep.addActionListener(e -> navigate.accept("Shop"));
        actions.add(change);
        actions.add(remove);
        actions.add(keep);
        left.add(actions, BorderLayout.SOUTH);
        body.add(left, BorderLayout.CENTER);
        totals.setLayout(new BorderLayout());
        totals.setPreferredSize(new java.awt.Dimension(300, 0));
        body.add(totals, BorderLayout.EAST);
        page.add(body, BorderLayout.CENTER);
        add(page);
        checkout.addActionListener(e -> openCheckout());
    }

    @Override
    public void refresh() {
        Async.run(this, api::cart, this::show);
    }

    private void show(CartDto c) {
        this.cart = c;
        table.setRows(c.items());
        totals.removeAll();
        PriceBreakdown p = c.pricing();
        JPanel rows = new JPanel(new GridLayout(0, 2, 0, 10));
        rows.setOpaque(false);
        line(rows, "Subtotal", Format.money(p.subtotal()), false);
        line(rows, "Discount", "-" + Format.money(p.discount()), false);
        line(rows, "Shipping", p.shipping().signum() == 0 ? "Free" : Format.money(p.shipping()), false);
        line(rows, "VAT", Format.money(p.tax()), false);
        line(rows, "Total", Format.money(p.total()), true);
        totals.add(Ui.label("Order summary", Font.BOLD, 16f, Theme.TEXT), BorderLayout.NORTH);
        JPanel mid = new JPanel(new BorderLayout());
        mid.setOpaque(false);
        mid.setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 0));
        mid.add(rows, BorderLayout.NORTH);
        totals.add(mid, BorderLayout.CENTER);
        checkout.setEnabled(c.checkoutReady());
        totals.add(checkout, BorderLayout.SOUTH);
        totals.revalidate();
        totals.repaint();
    }

    private static void line(JPanel p, String name, String value, boolean strong) {
        p.add(Ui.label(name, strong ? Font.BOLD : Font.PLAIN, strong ? 16f : 14f, strong ? Theme.TEXT : Theme.MUTED));
        JLabel v = Ui.label(value, strong ? Font.BOLD : Font.PLAIN, strong ? 16f : 14f, Theme.TEXT);
        v.setHorizontalAlignment(JLabel.RIGHT);
        p.add(v);
    }

    private void changeQuantity() {
        CartItemDto item = table.selected();
        if (item == null) return;
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(item.quantity(), 1, 20, 1));
        int ok = javax.swing.JOptionPane.showConfirmDialog(this, spinner, "Quantity for " + item.name(),
                javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.PLAIN_MESSAGE);
        if (ok == javax.swing.JOptionPane.OK_OPTION) {
            Async.run(this, () -> api.setCartQuantity(item.productId(), (Integer) spinner.getValue()), this::show);
        }
    }

    private void openCheckout() {
        if (cart == null) return;
        new CheckoutDialog(SwingUtilities.getWindowAncestor(this), api, cart, () -> {
            refresh();
            navigate.accept("My orders");
        }).setVisible(true);
    }
}
