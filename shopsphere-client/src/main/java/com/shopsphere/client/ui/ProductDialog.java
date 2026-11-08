package com.shopsphere.client.ui;

import com.shopsphere.client.network.ApiClient;
import com.shopsphere.client.util.Async;
import com.shopsphere.client.util.Format;
import com.shopsphere.common.dto.CatalogDtos.ProductDto;
import com.shopsphere.common.dto.CatalogDtos.ReviewDto;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;

/** Product details, add to cart, wishlist and verified purchase reviews. */
public class ProductDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final transient ApiClient api;
    private final transient ProductDto product;
    private final transient Runnable goToCart;
    private final JPanel reviewList = new JPanel();
    private final JSpinner qty = new JSpinner(new SpinnerNumberModel(1, 1, 20, 1));
    private final JSpinner rating = new JSpinner(new SpinnerNumberModel(5, 1, 5, 1));
    private final JTextArea comment = new JTextArea(3, 30);
    private final JLabel message = Ui.label(" ", Font.PLAIN, 13f, Theme.MUTED);

    public ProductDialog(Window owner, ApiClient api, ProductDto product, Runnable goToCart) {
        super(owner, product.name(), ModalityType.APPLICATION_MODAL);
        this.api = api;
        this.product = product;
        this.goToCart = goToCart;
        JPanel root = new JPanel(new BorderLayout(20, 16));
        root.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        root.setBackground(Theme.SURFACE);
        root.add(Ui.imageTile(product.name(), 200), BorderLayout.WEST);
        root.add(info(), BorderLayout.CENTER);
        root.add(reviews(), BorderLayout.SOUTH);
        setContentPane(root);
        setSize(780, 640);
        setLocationRelativeTo(owner);
        loadReviews();
    }

    private JPanel info() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(Ui.label("<html>" + Format.esc(product.name()) + "</html>", Font.BOLD, 22f, Theme.TEXT));
        p.add(Ui.muted("SKU " + product.sku() + (product.categoryName() == null ? "" : "  |  " + product.categoryName())));
        p.add(Box.createVerticalStrut(10));
        p.add(Ui.label(Format.money(product.price()), Font.BOLD, 26f, Theme.ACCENT));
        p.add(Box.createVerticalStrut(8));
        String desc = Format.esc(product.description());
        p.add(Ui.label("<html><div style='width:360px'>" + desc + "</div></html>", Font.PLAIN, 14f, Theme.TEXT));
        p.add(Box.createVerticalStrut(10));
        p.add(Ui.label(product.available() ? product.stock() + " in stock" : "Out of stock", Font.BOLD, 13f,
                product.available() ? Theme.SUCCESS : Theme.DANGER));
        p.add(Box.createVerticalStrut(12));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.add(qty);
        JButton add = Ui.button("Add to cart");
        add.setEnabled(product.available());
        add.addActionListener(e -> Async.run(this, () -> api.addToCart(product.id(), (Integer) qty.getValue()), cart -> {
            dispose();
            goToCart.run();
        }));
        JButton wish = Ui.button("Add to wishlist", Ui.Kind.SECONDARY);
        wish.addActionListener(e -> Async.run(this, () -> api.addWishlist(product.id()), list -> message.setText("Saved to your wishlist")));
        actions.add(add);
        actions.add(wish);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        p.add(actions);
        p.add(Box.createVerticalStrut(6));
        p.add(message);
        return p;
    }

    private JPanel reviews() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(700, 300));
        p.add(Ui.label("Reviews", Font.BOLD, 16f, Theme.TEXT), BorderLayout.NORTH);
        reviewList.setLayout(new BoxLayout(reviewList, BoxLayout.Y_AXIS));
        reviewList.setOpaque(false);
        JScrollPane scroll = new JScrollPane(reviewList);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        p.add(scroll, BorderLayout.CENTER);
        JPanel form = new JPanel(new BorderLayout(8, 4));
        form.setOpaque(false);
        comment.setLineWrap(true);
        comment.setBorder(Ui.inputBorder());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        top.setOpaque(false);
        top.add(Ui.muted("Your rating (1-5)"));
        top.add(rating);
        top.add(Ui.muted("Only customers with a delivered order can review"));
        form.add(top, BorderLayout.NORTH);
        form.add(comment, BorderLayout.CENTER);
        JButton post = Ui.button("Post review");
        post.addActionListener(e -> Async.run(this,
                () -> api.addReview(product.id(), (Integer) rating.getValue(), comment.getText()),
                r -> { comment.setText(""); loadReviews(); }));
        form.add(post, BorderLayout.EAST);
        p.add(form, BorderLayout.SOUTH);
        return p;
    }

    private void loadReviews() {
        Async.run(this, () -> api.reviews(product.id()), this::showReviews);
    }

    private void showReviews(List<ReviewDto> list) {
        reviewList.removeAll();
        if (list.isEmpty()) reviewList.add(Ui.muted("  No reviews yet. Be the first."));
        for (ReviewDto r : list) {
            JPanel row = new JPanel(new BorderLayout());
            row.setOpaque(false);
            row.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            row.add(Ui.label(Format.stars(r.rating()) + "  " + Format.esc(r.reviewerName()) + (r.verifiedPurchase() ? "  (verified purchase)" : ""),
                    Font.BOLD, 13f, Theme.TEXT), BorderLayout.NORTH);
            if (r.comment() != null && !r.comment().isBlank()) {
                row.add(Ui.label("<html>" + Format.esc(r.comment()) + "</html>", Font.PLAIN, 13f, Theme.MUTED), BorderLayout.CENTER);
            }
            reviewList.add(row);
        }
        reviewList.revalidate();
        reviewList.repaint();
    }
}
