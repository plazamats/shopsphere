package com.shopsphere.client.ui.admin;

import com.shopsphere.client.network.ApiClient;
import com.shopsphere.client.ui.DataTable;
import com.shopsphere.client.util.Format;
import com.shopsphere.common.dto.AdminDtos.AuditLogDto;
import java.util.List;

public class AuditPanel extends TablePage<AuditLogDto> {

    private static final long serialVersionUID = 1L;

    private final transient ApiClient api;

    public AuditPanel(ApiClient api) {
        super("Audit log", "Who did what and when. Super administrators also see security events", List.of(
                new DataTable.Column<>("When", a -> Format.date(a.createdAt())),
                new DataTable.Column<>("Who", AuditLogDto::actorEmail),
                new DataTable.Column<>("Action", AuditLogDto::action),
                new DataTable.Column<>("Type", AuditLogDto::category),
                new DataTable.Column<>("Entity", a -> a.entityType() + " " + (a.entityId() == null ? "" : a.entityId())),
                new DataTable.Column<>("Details", AuditLogDto::details)));
        this.api = api;
    }

    @Override
    public void refresh() {
        load(() -> api.audit(300));
    }
}
