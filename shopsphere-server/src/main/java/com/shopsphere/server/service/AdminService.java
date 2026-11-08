package com.shopsphere.server.service;

import com.shopsphere.common.dto.AdminDtos.AuditLogDto;
import com.shopsphere.common.dto.AdminDtos.AuditQuery;
import com.shopsphere.common.dto.AdminDtos.DashboardDto;
import com.shopsphere.common.dto.AdminDtos.ReportDto;
import com.shopsphere.common.dto.AdminDtos.SettingDto;
import com.shopsphere.common.dto.ShoppingDtos.PaymentDto;
import com.shopsphere.common.protocol.ReportType;
import com.shopsphere.common.protocol.Role;
import com.shopsphere.server.dao.AuditDao;
import com.shopsphere.server.dao.PaymentDao;
import com.shopsphere.server.dao.ReportDao;
import com.shopsphere.server.dao.SettingsDao;
import com.shopsphere.server.database.Database;
import com.shopsphere.server.security.Session;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Map;

/** Dashboard, reports, payments, audit log and system settings. */
public class AdminService {

    private final Database db;
    private final ReportDao reports;
    private final PaymentDao payments;
    private final AuditDao auditDao;
    private final SettingsDao settings;
    private final AuditService audit;

    public AdminService(Database db, ReportDao reports, PaymentDao payments, AuditDao auditDao,
                        SettingsDao settings, AuditService audit) {
        this.db = db;
        this.reports = reports;
        this.payments = payments;
        this.auditDao = auditDao;
        this.settings = settings;
        this.audit = audit;
    }

    public DashboardDto dashboard() {
        return db.read(reports::dashboard);
    }

    public ReportDto report(ReportType type) {
        if (type == null) throw ShopException.invalid("Choose a report");
        return db.read(c -> reports.report(c, type));
    }

    public ArrayList<PaymentDto> payments() {
        return db.read(payments::listAll);
    }

    /** Administrators see the business log. Security events are only visible to the super administrator. */
    public ArrayList<AuditLogDto> auditLog(Session actor, AuditQuery q) {
        int limit = q == null || q.limit() <= 0 ? 200 : Math.min(q.limit(), 1000);
        boolean includeSecurity = actor.role() == Role.SUPER_ADMIN;
        return db.read(c -> auditDao.list(c, limit, includeSecurity));
    }

    public ArrayList<SettingDto> listSettings() {
        return db.read(settings::list);
    }

    public SettingDto updateSetting(Session actor, SettingDto dto) {
        if (dto == null || dto.key() == null || dto.value() == null) throw ShopException.invalid("Setting and value are required");
        String value = dto.value().trim();
        validateSetting(dto.key(), value);
        return db.transaction(c -> {
            Map<String, String> before = settings.asMap(c);
            if (!before.containsKey(dto.key())) throw ShopException.notFound("Setting");
            settings.update(c, dto.key(), value, actor.userId());
            audit.admin(c, actor, "SETTING_CHANGED", "SETTING", dto.key(), before.get(dto.key()) + " to " + value);
            return settings.list(c).stream().filter(x -> x.key().equals(dto.key())).findFirst().orElseThrow();
        });
    }

    private static void validateSetting(String key, String value) {
        BigDecimal n;
        try {
            n = new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw ShopException.invalid("The value must be a number");
        }
        if (n.signum() < 0 || n.compareTo(new BigDecimal("1000000")) > 0) throw ShopException.invalid("The value is out of range");
        switch (key) {
            case "vat_rate" -> { if (n.compareTo(BigDecimal.ONE) > 0) throw ShopException.invalid("VAT rate must be between 0 and 1"); }
            case "discount_percent" -> { if (n.compareTo(new BigDecimal("100")) > 0) throw ShopException.invalid("Discount must be 0 to 100"); }
            case "max_item_quantity", "default_low_stock" -> {
                if (n.stripTrailingZeros().scale() > 0 || n.signum() == 0) throw ShopException.invalid("Must be a whole number above zero");
            }
            default -> { }
        }
    }
}
