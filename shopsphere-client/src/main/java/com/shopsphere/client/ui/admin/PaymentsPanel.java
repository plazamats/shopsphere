package com.shopsphere.client.ui.admin;

import com.shopsphere.client.network.ApiClient;
import com.shopsphere.client.ui.DataTable;
import com.shopsphere.client.util.Format;
import com.shopsphere.common.dto.ShoppingDtos.PaymentDto;
import java.util.List;

public class PaymentsPanel extends TablePage<PaymentDto> {

    private static final long serialVersionUID = 1L;

    private final transient ApiClient api;

    public PaymentsPanel(ApiClient api) {
        super("Payments", "All payments are simulated. No real money moves", List.of(
                new DataTable.Column<>("Reference", PaymentDto::reference),
                new DataTable.Column<>("Order", PaymentDto::orderNumber),
                new DataTable.Column<>("Amount", p -> Format.money(p.amount())),
                new DataTable.Column<>("Status", p -> Format.nice(p.status())),
                new DataTable.Column<>("Method", p -> p.simulated() ? "Simulated card" : p.method()),
                new DataTable.Column<>("Note", PaymentDto::failureReason),
                new DataTable.Column<>("Date", p -> Format.date(p.createdAt()))));
        this.api = api;
    }

    @Override
    public void refresh() {
        load(api::payments);
    }
}
