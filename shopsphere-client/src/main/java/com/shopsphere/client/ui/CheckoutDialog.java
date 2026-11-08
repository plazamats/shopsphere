package com.shopsphere.client.ui;

import com.shopsphere.client.network.ApiClient;
import com.shopsphere.client.util.Async;
import com.shopsphere.client.util.Format;
import com.shopsphere.common.dto.AuthDtos.AddressDto;
import com.shopsphere.common.dto.ShoppingDtos.CartDto;
import com.shopsphere.common.dto.ShoppingDtos.CheckoutResult;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Window;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Pick a delivery address and enter a SIMULATED card. The dialog creates one idempotency key per
 * attempt, so pressing Pay twice can never create two orders.
 */
public class CheckoutDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final transient ApiClient api;
    private final Runnable onDone;
    private final JComboBox<AddressItem> addresses = new JComboBox<>();
    private final JTextField card = Ui.field(20);
    private final JLabel message = Ui.label(" ", Font.PLAIN, 13f, Theme.DANGER);
    private final JButton pay;
    private String key = UUID.randomUUID().toString();

    private record AddressItem(AddressDto address) {
        @Override public String toString() { return address.oneLine(); }
    }

    public CheckoutDialog(Window owner, ApiClient api, CartDto cart, Runnable onDone) {
        super(owner, "Checkout", ModalityType.APPLICATION_MODAL);
        this.api = api;
        this.onDone = onDone;
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(22, 26, 22, 26));
        p.setBackground(Theme.SURFACE);
        p.add(left(Ui.label("Checkout", Font.BOLD, 22f, Theme.TEXT)));
        p.add(left(Ui.muted("Total to pay: " + Format.money(cart.pricing().total()))));
        p.add(Box.createVerticalStrut(14));
        JLabel sim = Ui.label("<html><b>SIMULATED PAYMENT.</b> No real money is charged. Use 4242 4242 4242 4242 to succeed, "
                + "a card ending 0002 is declined (try 4000 0000 0000 0002), ending 9995 has insufficient funds "
                + "(try 4000 0000 0000 9995).</html>", Font.PLAIN, 12f, new Color(0x92400E));
        sim.setOpaque(true);
        sim.setBackground(new Color(0xFEF3C7));
        sim.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        p.add(left(sim));
        p.add(Box.createVerticalStrut(14));
        p.add(left(Ui.label("DELIVERY ADDRESS", Font.BOLD, 12f, Theme.MUTED)));
        p.add(Box.createVerticalStrut(4));
        p.add(left(addresses));
        p.add(Box.createVerticalStrut(12));
        p.add(left(Ui.label("CARD NUMBER (SIMULATION)", Font.BOLD, 12f, Theme.MUTED)));
        p.add(Box.createVerticalStrut(4));
        p.add(left(card));
        p.add(Box.createVerticalStrut(8));
        p.add(left(message));
        p.add(Box.createVerticalStrut(8));
        pay = Ui.button("Pay " + Format.money(cart.pricing().total()));
        pay.addActionListener(e -> pay());
        p.add(left(pay));
        setContentPane(p);
        setSize(560, 520);
        setLocationRelativeTo(owner);
        Async.run(this, api::addresses, list -> {
            list.forEach(a -> addresses.addItem(new AddressItem(a)));
            if (list.isEmpty()) {
                message.setText("<html>Add a delivery address in your Profile first.</html>");
                pay.setEnabled(false);
            }
        });
    }

    private static <T extends java.awt.Component> T left(T c) {
        ((javax.swing.JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
        return c;
    }

    private void pay() {
        AddressItem a = (AddressItem) addresses.getSelectedItem();
        if (a == null) return;
        message.setText(" ");
        pay.setEnabled(false);
        String number = card.getText();
        Async.run(this, () -> api.checkout(a.address().id(), number, key), this::finished, err -> {
            pay.setEnabled(true);
            message.setText("<html>" + Format.esc(err.getMessage()) + "</html>");
        });
    }

    private void finished(CheckoutResult r) {
        if (r.paymentSucceeded()) {
            Async.showInfo(this, "Order " + r.order().orderNumber() + " placed.\n" + r.message());
            dispose();
            onDone.run();
        } else {
            // a failed payment is a finished attempt. A new attempt needs a new key.
            key = UUID.randomUUID().toString();
            pay.setEnabled(true);
            message.setText("<html>" + Format.esc(r.message()) + ". Your cart is kept, try another card.</html>");
        }
    }
}
