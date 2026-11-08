package com.shopsphere.client.ui;

import com.shopsphere.client.network.ApiClient;
import com.shopsphere.client.util.Async;
import com.shopsphere.client.util.Format;
import com.shopsphere.common.dto.AuthDtos.AddressDto;
import com.shopsphere.common.dto.AuthDtos.UpdateProfileRequest;
import com.shopsphere.common.dto.AuthDtos.UserDto;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/** Profile, password and delivery addresses. */
public class ProfilePanel extends JPanel implements Refreshable {

    private static final long serialVersionUID = 1L;

    private final transient ApiClient api;
    private final JTextField first = Ui.field(14);
    private final JTextField last = Ui.field(14);
    private final JTextField phone = Ui.field(14);
    private final JLabel email = Ui.muted(" ");
    private final JPasswordField current = Ui.password(14);
    private final JPasswordField next = Ui.password(14);
    private final DataTable<AddressDto> addresses = new DataTable<>(List.of(
            new DataTable.Column<>("Recipient", AddressDto::recipient),
            new DataTable.Column<>("Address", a -> a.line1() + ", " + a.city() + ", " + a.province() + " " + a.postalCode())));

    public ProfilePanel(ApiClient api) {
        super(new BorderLayout());
        this.api = api;
        JPanel page = Ui.page("Profile", "Your details, password and delivery addresses");
        JPanel grid = new JPanel(new GridLayout(1, 2, 16, 0));
        grid.setOpaque(false);
        grid.add(accountCard());
        grid.add(addressCard());
        page.add(grid, BorderLayout.CENTER);
        add(page);
    }

    private JPanel accountCard() {
        JPanel c = Ui.card();
        c.setLayout(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 0, 5, 8);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;
        g.gridx = 0; g.gridy = row; g.gridwidth = 2; c.add(email, g); row++;
        g.gridwidth = 1;
        row = addField(c, g, row, "First name", first);
        row = addField(c, g, row, "Last name", last);
        row = addField(c, g, row, "Phone", phone);
        JButton save = Ui.button("Save details");
        save.addActionListener(e -> Async.run(this, () -> api.updateProfile(new UpdateProfileRequest(
                first.getText().trim(), last.getText().trim(), phone.getText().trim().isEmpty() ? null : phone.getText().trim())),
                u -> Async.showInfo(this, "Details saved")));
        g.gridx = 1; g.gridy = row++; c.add(save, g);
        g.gridx = 0; g.gridy = row++; g.gridwidth = 2; c.add(Ui.muted(" "), g);
        g.gridwidth = 1;
        row = addField(c, g, row, "Current password", current);
        row = addField(c, g, row, "New password", next);
        JButton change = Ui.button("Change password", Ui.Kind.SECONDARY);
        change.addActionListener(e -> Async.run(this,
                () -> { api.changePassword(new String(current.getPassword()), new String(next.getPassword())); return Boolean.TRUE; },
                ok -> { current.setText(""); next.setText(""); Async.showInfo(this, "Password changed"); }));
        g.gridx = 1; g.gridy = row; c.add(change, g);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(c, BorderLayout.NORTH);
        return wrap;
    }

    private static int addField(JPanel c, GridBagConstraints g, int row, String name, java.awt.Component field) {
        g.gridx = 0; g.gridy = row; g.weightx = 0; c.add(Ui.muted(name), g);
        g.gridx = 1; g.weightx = 1; c.add(field, g);
        return row + 1;
    }

    private JPanel addressCard() {
        JPanel c = Ui.card();
        c.setLayout(new BorderLayout(0, 10));
        c.add(Ui.label("Delivery addresses", java.awt.Font.BOLD, 16f, Theme.TEXT), BorderLayout.NORTH);
        c.add(addresses, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        JButton add = Ui.button("Add address");
        add.addActionListener(e -> addAddress());
        JButton remove = Ui.button("Remove", Ui.Kind.SECONDARY);
        remove.addActionListener(e -> {
            AddressDto a = addresses.selected();
            if (a != null) Async.run(this, () -> api.deleteAddress(a.id()), addresses::setRows);
        });
        actions.add(add);
        actions.add(remove);
        c.add(actions, BorderLayout.SOUTH);
        return c;
    }

    private void addAddress() {
        JTextField recipient = Ui.field(18);
        JTextField line1 = Ui.field(18);
        JTextField line2 = Ui.field(18);
        JTextField city = Ui.field(18);
        JTextField province = Ui.field(18);
        JTextField postal = Ui.field(18);
        JTextField phoneField = Ui.field(18);
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Recipient")); form.add(recipient);
        form.add(new JLabel("Address line 1")); form.add(line1);
        form.add(new JLabel("Address line 2 (optional)")); form.add(line2);
        form.add(new JLabel("City")); form.add(city);
        form.add(new JLabel("Province")); form.add(province);
        form.add(new JLabel("Postal code")); form.add(postal);
        form.add(new JLabel("Phone (optional)")); form.add(phoneField);
        int ok = javax.swing.JOptionPane.showConfirmDialog(this, form, "New address", javax.swing.JOptionPane.OK_CANCEL_OPTION,
                javax.swing.JOptionPane.PLAIN_MESSAGE);
        if (ok != javax.swing.JOptionPane.OK_OPTION) return;
        AddressDto dto = new AddressDto(null, recipient.getText().trim(), line1.getText().trim(), blank(line2), city.getText().trim(),
                province.getText().trim(), postal.getText().trim(), "South Africa", blank(phoneField));
        Async.run(this, () -> api.addAddress(dto), a -> refresh());
    }

    private static String blank(JTextField f) {
        return f.getText().trim().isEmpty() ? null : f.getText().trim();
    }

    @Override
    public void refresh() {
        Async.run(this, api::profile, (UserDto u) -> {
            email.setText(u.email() + "   |   Member since " + Format.date(u.createdAt()));
            first.setText(u.firstName());
            last.setText(u.lastName());
            phone.setText(u.phone() == null ? "" : u.phone());
        });
        Async.run(this, api::addresses, addresses::setRows);
    }
}
