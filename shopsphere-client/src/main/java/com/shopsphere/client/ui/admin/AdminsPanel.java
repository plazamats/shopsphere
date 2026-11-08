package com.shopsphere.client.ui.admin;

import com.shopsphere.client.network.ApiClient;
import com.shopsphere.client.ui.DataTable;
import com.shopsphere.client.ui.Ui;
import com.shopsphere.client.util.Format;
import com.shopsphere.common.dto.AuthDtos.CreateAdminRequest;
import com.shopsphere.common.dto.AuthDtos.UserDto;
import com.shopsphere.common.protocol.Role;
import com.shopsphere.common.protocol.UserStatus;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/** Super administrator only. The server enforces this as well, it is not just a hidden menu. */
public class AdminsPanel extends TablePage<UserDto> {

    private static final long serialVersionUID = 1L;

    private final transient ApiClient api;

    public AdminsPanel(ApiClient api) {
        super("Administrators", "Create administrators and control their access", List.of(
                new DataTable.Column<>("Name", UserDto::fullName),
                new DataTable.Column<>("Email", UserDto::email),
                new DataTable.Column<>("Role", u -> Format.nice(u.role())),
                new DataTable.Column<>("Status", u -> Format.nice(u.status())),
                new DataTable.Column<>("Last login", u -> Format.date(u.lastLoginAt()))));
        this.api = api;
        action("New administrator", Ui.Kind.PRIMARY, this::create);
        onSelected("Suspend", Ui.Kind.DANGER, u -> call(() -> api.setUserStatus(u.id(), UserStatus.SUSPENDED), r -> refresh()));
        onSelected("Reactivate", Ui.Kind.SECONDARY, u -> call(() -> api.setUserStatus(u.id(), UserStatus.ACTIVE), r -> refresh()));
        onSelected("Make customer", Ui.Kind.SECONDARY, u -> call(() -> api.setRole(u.id(), Role.CUSTOMER), r -> refresh()));
    }

    private void create() {
        JTextField first = Ui.field(16);
        JTextField last = Ui.field(16);
        JTextField email = Ui.field(16);
        JPasswordField password = Ui.password(16);
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("First name")); form.add(first);
        form.add(new JLabel("Last name")); form.add(last);
        form.add(new JLabel("Email")); form.add(email);
        form.add(new JLabel("Temporary password")); form.add(password);
        int ok = JOptionPane.showConfirmDialog(this, form, "New administrator", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok == JOptionPane.OK_OPTION) {
            call(() -> api.createAdmin(new CreateAdminRequest(email.getText().trim(), new String(password.getPassword()),
                    first.getText().trim(), last.getText().trim())), r -> refresh());
        }
    }

    @Override
    public void refresh() {
        load(api::admins);
    }
}
