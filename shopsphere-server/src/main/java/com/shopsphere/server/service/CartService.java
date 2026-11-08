package com.shopsphere.server.service;

import com.shopsphere.common.dto.ShoppingDtos.CartDto;
import com.shopsphere.common.dto.ShoppingDtos.CartItemDto;
import com.shopsphere.common.dto.ShoppingDtos.CartUpdate;
import com.shopsphere.common.protocol.ErrorCode;
import com.shopsphere.server.dao.CartDao;
import com.shopsphere.server.dao.CartDao.CartRow;
import com.shopsphere.server.dao.ProductDao;
import com.shopsphere.server.dao.SettingsDao;
import com.shopsphere.server.database.Database;
import com.shopsphere.server.security.Session;
import com.shopsphere.server.validation.Validator;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * The cart stores only product ids and quantities. Prices, line totals and the total are calculated here,
 * from the current product prices, every time the cart is viewed.
 */
public class CartService {

    private final Database db;
    private final CartDao carts;
    private final ProductDao products;
    private final SettingsDao settings;

    public CartService(Database db, CartDao carts, ProductDao products, SettingsDao settings) {
        this.db = db;
        this.carts = carts;
        this.products = products;
        this.settings = settings;
    }

    public CartDto view(Session s) {
        return db.transaction(c -> build(c, carts.ensureCart(c, s.userId())));
    }

    /** Add the quantity to what is already in the cart. */
    public CartDto add(Session s, CartUpdate u) {
        if (u == null) throw ShopException.invalid("Product and quantity are required");
        return db.transaction(c -> {
            PricingConfig cfg = PricingConfig.from(settings.asMap(c));
            int qty = Validator.quantity(u.quantity(), cfg.maxItemQuantity());
            long cartId = carts.ensureCart(c, s.userId());
            int newQty = carts.quantityOf(c, cartId, u.productId()) + qty;
            check(c, u.productId(), newQty, cfg);
            carts.setQuantity(c, cartId, u.productId(), newQty);
            return build(c, cartId);
        });
    }

    /** Set the quantity to an exact value. */
    public CartDto update(Session s, CartUpdate u) {
        if (u == null) throw ShopException.invalid("Product and quantity are required");
        return db.transaction(c -> {
            PricingConfig cfg = PricingConfig.from(settings.asMap(c));
            int qty = Validator.quantity(u.quantity(), cfg.maxItemQuantity());
            long cartId = carts.ensureCart(c, s.userId());
            if (carts.quantityOf(c, cartId, u.productId()) == 0) throw ShopException.notFound("Cart item");
            check(c, u.productId(), qty, cfg);
            carts.setQuantity(c, cartId, u.productId(), qty);
            return build(c, cartId);
        });
    }

    public CartDto remove(Session s, long productId) {
        return db.transaction(c -> {
            long cartId = carts.ensureCart(c, s.userId());
            carts.remove(c, cartId, productId);
            return build(c, cartId);
        });
    }

    /** The product must be on sale and have enough stock for the wanted quantity. */
    private void check(Connection c, long productId, int wanted, PricingConfig cfg) throws SQLException {
        var product = products.find(c, productId).orElseThrow(() -> ShopException.notFound("Product"));
        if (!product.active()) throw ShopException.notFound("Product");
        if (wanted > cfg.maxItemQuantity()) {
            throw ShopException.invalid("You can buy at most " + cfg.maxItemQuantity() + " of one product");
        }
        if (wanted > product.stock()) {
            throw new ShopException(ErrorCode.OUT_OF_STOCK, product.stock() == 0
                    ? product.name() + " is out of stock"
                    : "Only " + product.stock() + " of " + product.name() + " left in stock");
        }
    }

    private CartDto build(Connection c, long cartId) throws SQLException {
        PricingConfig cfg = PricingConfig.from(settings.asMap(c));
        List<CartRow> rows = carts.items(c, cartId);
        ArrayList<CartItemDto> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        boolean ready = !rows.isEmpty();
        for (CartRow r : rows) {
            BigDecimal line = PricingCalculator.money(r.unitPrice().multiply(BigDecimal.valueOf(r.quantity())));
            boolean ok = r.active() && r.stock() >= r.quantity();
            if (!ok) ready = false;
            subtotal = subtotal.add(line);
            items.add(new CartItemDto(r.productId(), r.sku(), r.name(), r.unitPrice(), r.quantity(), line, r.stock(), ok));
        }
        return new CartDto(items, PricingCalculator.calculate(subtotal, cfg), ready);
    }
}
