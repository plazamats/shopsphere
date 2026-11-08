package com.shopsphere.server.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shopsphere.common.dto.AdminDtos.InventoryDto;
import com.shopsphere.common.dto.AdminDtos.StockAdjustRequest;
import com.shopsphere.common.dto.AdminDtos.StockMovementDto;
import com.shopsphere.common.dto.CatalogDtos.CategoryDto;
import com.shopsphere.common.dto.CatalogDtos.ProductDto;
import com.shopsphere.common.dto.CatalogDtos.ProductQuery;
import com.shopsphere.common.dto.CatalogDtos.ProductSaveRequest;
import com.shopsphere.common.protocol.Command;
import com.shopsphere.common.protocol.ErrorCode;
import com.shopsphere.common.protocol.StockReason;
import com.shopsphere.server.support.Helpers;
import com.shopsphere.server.support.TestEnv.TestClient;
import java.math.BigDecimal;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CatalogIntegrationTest {

    private Helpers h;

    @BeforeEach
    void setUp() {
        h = Helpers.fresh();
    }

    private ProductSaveRequest newProduct(String sku, String price, int stock) {
        ArrayList<CategoryDto> cats = h.admin().ok(Command.LIST_CATEGORIES, null);
        return new ProductSaveRequest(null, sku, "Test " + sku, "A test product", new BigDecimal(price), cats.get(0).id(), true,
                new ArrayList<>(java.util.List.of("images/test.png")), stock);
    }

    @Test
    void adminCreatesEditsAndArchivesAProduct() {
        TestClient admin = h.admin();
        ProductDto created = admin.ok(Command.SAVE_PRODUCT, newProduct("TEST-001", "49.90", 10));
        assertEquals(10, created.stock());
        assertEquals(new BigDecimal("49.90"), created.price());

        ProductDto edited = admin.ok(Command.SAVE_PRODUCT, new ProductSaveRequest(created.id(), "TEST-001", "Renamed", "New text",
                new BigDecimal("59.90"), created.categoryId(), true, created.imageUrls(), 10));
        assertEquals("Renamed", edited.name());
        assertEquals(new BigDecimal("59.90"), edited.price());

        admin.ok(Command.ARCHIVE_PRODUCT, created.id());
        TestClient customer = h.customer();
        assertEquals(ErrorCode.NOT_FOUND, customer.fails(Command.GET_PRODUCT, created.id()));
        ArrayList<ProductDto> visible = customer.ok(Command.LIST_PRODUCTS, new ProductQuery("TEST-001", null, true));
        assertTrue(visible.isEmpty(), "customers must never see archived products, even when they ask for them");
    }

    @Test
    void duplicateSkuAndBadValuesAreRefused() {
        TestClient admin = h.admin();
        admin.ok(Command.SAVE_PRODUCT, newProduct("TEST-002", "10.00", 1));
        assertEquals(ErrorCode.CONFLICT, admin.fails(Command.SAVE_PRODUCT, newProduct("TEST-002", "10.00", 1)));
        assertEquals(ErrorCode.VALIDATION_FAILED, admin.fails(Command.SAVE_PRODUCT, newProduct("TEST-003", "-5.00", 1)));
        assertEquals(ErrorCode.VALIDATION_FAILED, admin.fails(Command.SAVE_PRODUCT, newProduct("TEST-004", "10.00", -3)));
    }

    @Test
    void customersCannotWriteProducts() {
        TestClient c = h.customer();
        assertEquals(ErrorCode.FORBIDDEN, c.fails(Command.SAVE_PRODUCT, newProduct("HACK-001", "1.00", 1)));
        assertEquals(ErrorCode.FORBIDDEN, c.fails(Command.ARCHIVE_PRODUCT, 1L));
    }

    @Test
    void searchAndCategoryFilterWork() {
        TestClient c = h.customer();
        ArrayList<ProductDto> all = c.ok(Command.LIST_PRODUCTS, null);
        assertEquals(20, all.size());
        ArrayList<ProductDto> kettle = c.ok(Command.LIST_PRODUCTS, new ProductQuery("kettle", null, false));
        assertEquals(1, kettle.size());
        long categoryId = all.get(0).categoryId();
        ArrayList<ProductDto> inCategory = c.ok(Command.LIST_PRODUCTS, new ProductQuery(null, categoryId, false));
        assertTrue(inCategory.size() > 0 && inCategory.size() < 20);
        assertTrue(inCategory.stream().allMatch(p -> p.categoryId() == categoryId));
    }

    @Test
    void sqlInjectionAndWildcardsInSearchAreHarmless() {
        TestClient c = h.customer();
        String[] evil = {"'; DROP TABLE products; --", "' OR '1'='1", "%", "_", "\\", "kettle' UNION SELECT password_hash FROM users --"};
        for (String s : evil) {
            ArrayList<ProductDto> r = c.ok(Command.LIST_PRODUCTS, new ProductQuery(s, null, false));
            assertTrue(r.size() < 20, "search '" + s + "' must not return everything");
        }
        ArrayList<ProductDto> all = c.ok(Command.LIST_PRODUCTS, null);
        assertEquals(20, all.size(), "the products table must be untouched");
    }

    @Test
    void stockAdjustmentsAreRecordedAndNeverGoNegative() {
        TestClient admin = h.admin();
        ProductDto p = admin.ok(Command.SAVE_PRODUCT, newProduct("TEST-005", "10.00", 5));
        InventoryDto after = admin.ok(Command.ADJUST_STOCK, new StockAdjustRequest(p.id(), 7, StockReason.RESTOCK, "delivery"));
        assertEquals(12, after.quantity());
        after = admin.ok(Command.ADJUST_STOCK, new StockAdjustRequest(p.id(), -2, StockReason.DAMAGE, "dropped"));
        assertEquals(10, after.quantity());
        assertEquals(ErrorCode.VALIDATION_FAILED, admin.fails(Command.ADJUST_STOCK, new StockAdjustRequest(p.id(), -11, StockReason.CORRECTION, null)));
        assertEquals(10, h.stock("TEST-005"));
        assertEquals(ErrorCode.VALIDATION_FAILED, admin.fails(Command.ADJUST_STOCK, new StockAdjustRequest(p.id(), 0, StockReason.RESTOCK, null)));
        assertEquals(ErrorCode.VALIDATION_FAILED, admin.fails(Command.ADJUST_STOCK, new StockAdjustRequest(p.id(), 5, StockReason.PURCHASE, null)));
        ArrayList<StockMovementDto> history = admin.ok(Command.LIST_STOCK_MOVEMENTS, p.id());
        assertEquals(3, history.size());
        assertEquals(10, history.get(0).resultingQuantity());
    }

    @Test
    void lowStockIsFlagged() {
        TestClient admin = h.admin();
        ArrayList<InventoryDto> inv = admin.ok(Command.LIST_INVENTORY, null);
        InventoryDto phone = inv.stream().filter(i -> i.sku().equals("ELEC-002")).findFirst().orElseThrow();
        assertTrue(phone.lowStock());
        assertFalse(inv.stream().filter(i -> i.sku().equals("BOOK-001")).findFirst().orElseThrow().lowStock());
    }

    @Test
    void categoriesCanBeManagedButNotDeletedWhileInUse() {
        TestClient admin = h.admin();
        CategoryDto c = admin.ok(Command.SAVE_CATEGORY, new CategoryDto(null, "Garden", "Plants and tools"));
        assertEquals("Garden", c.name());
        assertEquals(ErrorCode.CONFLICT, admin.fails(Command.SAVE_CATEGORY, new CategoryDto(null, "Garden", "again")));
        ArrayList<CategoryDto> all = admin.ok(Command.LIST_CATEGORIES, null);
        long used = h.product(admin, "BOOK-001").categoryId();
        assertEquals(ErrorCode.CONFLICT, admin.fails(Command.DELETE_CATEGORY, used));
        admin.ok(Command.DELETE_CATEGORY, c.id());
        assertEquals(all.size() - 1, ((ArrayList<?>) admin.ok(Command.LIST_CATEGORIES, null)).size());
    }
}
