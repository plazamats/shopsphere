package com.shopsphere.server.service;

import com.shopsphere.common.dto.CatalogDtos.CategoryDto;
import com.shopsphere.common.dto.CatalogDtos.ProductDto;
import com.shopsphere.common.dto.CatalogDtos.ProductQuery;
import com.shopsphere.common.dto.CatalogDtos.ProductSaveRequest;
import com.shopsphere.common.protocol.ErrorCode;
import com.shopsphere.common.protocol.Role;
import com.shopsphere.server.dao.CategoryDao;
import com.shopsphere.server.dao.InventoryDao;
import com.shopsphere.server.dao.ProductDao;
import com.shopsphere.server.dao.SettingsDao;
import com.shopsphere.server.database.Database;
import com.shopsphere.server.security.Session;
import com.shopsphere.server.validation.Validator;
import com.shopsphere.common.protocol.StockReason;
import java.util.ArrayList;
import java.util.List;

/** Browsing is public. Creating and changing products and categories is for administrators. */
public class CatalogService {

    private static final int MAX_IMAGES = 5;
    private static final int MAX_INITIAL_STOCK = 1_000_000;

    private final Database db;
    private final CategoryDao categories;
    private final ProductDao products;
    private final InventoryDao inventory;
    private final SettingsDao settings;
    private final AuditService audit;

    public CatalogService(Database db, CategoryDao categories, ProductDao products, InventoryDao inventory,
                          SettingsDao settings, AuditService audit) {
        this.db = db;
        this.categories = categories;
        this.products = products;
        this.inventory = inventory;
        this.settings = settings;
        this.audit = audit;
    }

    public ArrayList<CategoryDto> listCategories() {
        return db.read(categories::list);
    }

    /** role is null for a visitor who is not logged in. Only administrators may see archived products. */
    public ArrayList<ProductDto> listProducts(ProductQuery q, Role role) {
        ProductQuery query = q == null ? new ProductQuery(null, null, false) : q;
        boolean includeInactive = query.includeInactive() && role != null && role.isAdmin();
        String search = Validator.search(query.search());
        return db.read(c -> products.search(c, search, query.categoryId(), includeInactive));
    }

    public ProductDto getProduct(long id, Role role) {
        ProductDto p = db.read(c -> products.find(c, id)).orElseThrow(() -> ShopException.notFound("Product"));
        if (!p.active() && (role == null || !role.isAdmin())) throw ShopException.notFound("Product");
        return p;
    }

    public ProductDto saveProduct(Session actor, ProductSaveRequest r) {
        if (r == null) throw ShopException.invalid("Product details are required");
        String sku = Validator.sku(r.sku());
        String name = Validator.text(r.name(), "Name", 150);
        String description = Validator.optionalText(r.description(), "Description", 2000);
        var price = Validator.price(r.price());
        List<String> images = new ArrayList<>();
        if (r.imageUrls() != null) {
            if (r.imageUrls().size() > MAX_IMAGES) throw ShopException.invalid("At most " + MAX_IMAGES + " images");
            for (String url : r.imageUrls()) images.add(Validator.imageRef(url));
        }
        int initialStock = Validator.nonNegative(r.initialStock(), "Initial stock");
        if (initialStock > MAX_INITIAL_STOCK) throw ShopException.invalid("Initial stock is too large");
        ProductSaveRequest clean = new ProductSaveRequest(r.id(), sku, name, description, price, r.categoryId(),
                r.active(), new ArrayList<>(images), initialStock);

        return db.transaction(c -> {
            if (clean.categoryId() != null && categories.find(c, clean.categoryId()).isEmpty()) {
                throw ShopException.invalid("The category does not exist");
            }
            if (products.skuExists(c, sku, clean.id())) {
                throw new ShopException(ErrorCode.CONFLICT, "A product with this SKU already exists");
            }
            long id;
            if (clean.id() == null) {
                id = products.insert(c, clean);
                int threshold = PricingConfig.from(settings.asMap(c)).defaultLowStock();
                inventory.insert(c, id, initialStock, threshold);
                if (initialStock > 0) {
                    inventory.addMovement(c, id, initialStock, StockReason.INITIAL, null, "Product created", actor.userId());
                }
                audit.admin(c, actor, "PRODUCT_CREATED", "PRODUCT", String.valueOf(id), sku + " " + name);
            } else {
                ProductDto before = products.find(c, clean.id()).orElseThrow(() -> ShopException.notFound("Product"));
                products.update(c, clean);
                id = clean.id();
                audit.admin(c, actor, "PRODUCT_UPDATED", "PRODUCT", String.valueOf(id),
                        sku + " price " + before.price() + " to " + price);
            }
            products.replaceImages(c, id, images);
            return products.find(c, id).orElseThrow();
        });
    }

    /** Archiving keeps the product in old orders. The product just stops being sold. */
    public ProductDto archiveProduct(Session actor, long id) {
        return db.transaction(c -> {
            if (!products.setActive(c, id, false)) throw ShopException.notFound("Product");
            audit.admin(c, actor, "PRODUCT_ARCHIVED", "PRODUCT", String.valueOf(id), null);
            return products.find(c, id).orElseThrow();
        });
    }

    public CategoryDto saveCategory(Session actor, CategoryDto dto) {
        if (dto == null) throw ShopException.invalid("Category details are required");
        String name = Validator.text(dto.name(), "Category name", 80);
        String description = Validator.optionalText(dto.description(), "Description", 255);
        try {
            return db.transaction(c -> {
                long id;
                if (dto.id() == null) {
                    id = categories.insert(c, name, description);
                    audit.admin(c, actor, "CATEGORY_CREATED", "CATEGORY", String.valueOf(id), name);
                } else {
                    if (!categories.update(c, dto.id(), name, description)) throw ShopException.notFound("Category");
                    id = dto.id();
                    audit.admin(c, actor, "CATEGORY_UPDATED", "CATEGORY", String.valueOf(id), name);
                }
                return categories.find(c, id).orElseThrow();
            });
        } catch (RuntimeException e) {
            if (Database.isUniqueViolation(e)) throw new ShopException(ErrorCode.CONFLICT, "A category with this name already exists");
            throw e;
        }
    }

    public void deleteCategory(Session actor, long id) {
        db.transaction(c -> {
            if (categories.find(c, id).isEmpty()) throw ShopException.notFound("Category");
            if (categories.countProducts(c, id) > 0) {
                throw new ShopException(ErrorCode.CONFLICT, "The category still has products. Move or archive them first");
            }
            categories.delete(c, id);
            audit.admin(c, actor, "CATEGORY_DELETED", "CATEGORY", String.valueOf(id), null);
            return null;
        });
    }
}
