package com.shopsphere.client.ui.admin;

import com.shopsphere.client.network.ApiClient;
import com.shopsphere.client.ui.DataTable;
import com.shopsphere.client.ui.Ui;
import com.shopsphere.common.dto.AdminDtos.SettingDto;
import java.util.List;
import javax.swing.JOptionPane;

public class SettingsPanel extends TablePage<SettingDto> {

    private static final long serialVersionUID = 1L;

    private final transient ApiClient api;

    public SettingsPanel(ApiClient api) {
        super("System settings", "VAT, discounts, shipping and limits. Changes are audited", List.of(
                new DataTable.Column<>("Setting", SettingDto::key),
                new DataTable.Column<>("Value", SettingDto::value),
                new DataTable.Column<>("Description", SettingDto::description)));
        this.api = api;
        onSelected("Change value", Ui.Kind.PRIMARY, s -> {
            String v = JOptionPane.showInputDialog(this, s.key() + " (" + s.description() + ")", s.value());
            if (v != null) call(() -> api.updateSetting(s.key(), v), r -> refresh());
        });
    }

    @Override
    public void refresh() {
        load(api::settings);
    }
}
