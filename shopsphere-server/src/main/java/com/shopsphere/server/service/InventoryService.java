package com.shopsphere.server.service;

import com.shopsphere.common.dto.AdminDtos.InventoryDto;
import com.shopsphere.common.dto.AdminDtos.StockAdjustRequest;
import com.shopsphere.common.dto.AdminDtos.StockMovementDto;
import com.shopsphere.common.protocol.ErrorCode;
import com.shopsphere.server.dao.InventoryDao;
import com.shopsphere.server.database.Database;
import com.shopsphere.server.security.Session;
import com.shopsphere.server.validation.Validator;
import java.util.ArrayList;
import java.util.List;

/** Stock levels, manual adjustments and the movement history. */
public class InventoryService {

    private static final int MAX_ADJUSTMENT = 100_000;

    private final Database db;
    private final InventoryDao inventory;
    private final AuditService audit;

    public InventoryService(Database db, InventoryDao inventory, AuditService audit) {
        this.db = db;
        this.inventory = inventory;
        this.audit = audit;
    }

    public ArrayList<InventoryDto> list() {
        return db.read(inventory::list);
    }

    public ArrayList<StockMovementDto> movements(Long productId) {
        return db.read(c -> inventory.movements(c, productId, 200));
    }

    /** Add (positive) or remove (negative) stock. The quantity can never become negative. */
    public InventoryDto adjust(Session actor, StockAdjustRequest r) {
        if (r == null || r.reason() == null) throw ShopException.invalid("Product, change and reason are required");
        if (!r.reason().isManual()) throw ShopException.invalid("That reason cannot be used for a manual adjustment");
        if (r.delta() == 0) throw ShopException.invalid("The change cannot be zero");
        if (Math.abs((long) r.delta()) > MAX_ADJUSTMENT) throw ShopException.invalid("The change is too large");
        String note = Validator.optionalText(r.note(), "Note", 255);
        return db.transaction(c -> {
            List<Long> ids = List.of(r.productId());
            int current = inventory.lockQuantities(c, ids).getOrDefault(r.productId(), -1);
            if (current < 0) throw ShopException.notFound("Product");
            if (r.delta() > 0) {
                inventory.increment(c, r.productId(), r.delta());
            } else if (!inventory.decrementIfAvailable(c, r.productId(), -r.delta())) {
                throw new ShopException(ErrorCode.VALIDATION_FAILED,
                        "Stock cannot go below zero. Only " + current + " in stock");
            }
            inventory.addMovement(c, r.productId(), r.delta(), r.reason(), null, note, actor.userId());
            audit.admin(c, actor, "STOCK_ADJUSTED", "PRODUCT", String.valueOf(r.productId()),
                    r.reason() + " " + (r.delta() > 0 ? "+" : "") + r.delta());
            return inventory.find(c, r.productId()).orElseThrow();
        });
    }
}
