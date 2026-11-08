package com.shopsphere.client.ui.admin;

import com.shopsphere.client.network.ApiClient;
import com.shopsphere.client.ui.Ui;
import com.shopsphere.client.util.Format;
import com.shopsphere.common.dto.AuthDtos.UserDto;
import com.shopsphere.common.protocol.UserStatus;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

public class CustomersPanel extends TablePage<UserDto> {

    private static final long serialVersionUID = 1L;

    private final transient ApiClient api;
    private final JTextField search = Ui.field(22);

    public CustomersPanel(ApiClient api) {
        super("Customers", "Search, suspend and reactivate customer accounts", List.of(
                new com.shopsphere.client.ui.DataTable.Column<>("Name", UserDto::fullName),
                new com.shopsphere.client.ui.DataTable.Column<>("Email", UserDto::email),
                new com.shopsphere.client.ui.DataTable.Column<>("Phone", UserDto::phone),
                new com.shopsphere.client.ui.DataTable.Column<>("Status", u -> Format.nice(u.status())),
                new com.shopsphere.client.ui.DataTable.Column<>("Joined", u -> Format.date(u.createdAt())),
                new com.shopsphere.client.ui.DataTable.Column<>("Last login", u -> Format.date(u.lastLoginAt()))));
        this.api = api;
        JButton go = Ui.button("Search");
        go.addActionListener(e -> refresh());
        search.addActionListener(e -> refresh());
        addTop(search);
        addTop(go);
        onSelected("Suspend", Ui.Kind.DANGER, u -> change(u, UserStatus.SUSPENDED));
        onSelected("Reactivate", Ui.Kind.PRIMARY, u -> change(u, UserStatus.ACTIVE));
    }

    private void change(UserDto u, UserStatus status) {
        int ok = JOptionPane.showConfirmDialog(this, (status == UserStatus.SUSPENDED ? "Suspend " : "Reactivate ") + u.email() + "?",
                "Confirm", JOptionPane.YES_NO_OPTION);
        if (ok == JOptionPane.YES_OPTION) call(() -> api.setUserStatus(u.id(), status), r -> refresh());
    }

    @Override
    public void refresh() {
        String q = search.getText().trim();
        load(() -> api.customers(q.isEmpty() ? null : q));
    }
}
