package com.shopsphere.server.service;

import com.shopsphere.common.dto.CatalogDtos.ProductDto;
import com.shopsphere.server.dao.ProductDao;
import com.shopsphere.server.dao.WishlistDao;
import com.shopsphere.server.database.Database;
import com.shopsphere.server.security.Session;
import java.util.ArrayList;
import java.util.List;

public class WishlistService {

    private final Database db;
    private final WishlistDao wishlists;
    private final ProductDao products;

    public WishlistService(Database db, WishlistDao wishlists, ProductDao products) {
        this.db = db;
        this.wishlists = wishlists;
        this.products = products;
    }

    public ArrayList<ProductDto> get(Session s) {
        return db.transaction(c -> load(c, wishlists.ensure(c, s.userId())));
    }

    public ArrayList<ProductDto> add(Session s, long productId) {
        return db.transaction(c -> {
            ProductDto p = products.find(c, productId).orElseThrow(() -> ShopException.notFound("Product"));
            if (!p.active()) throw ShopException.notFound("Product");
            long id = wishlists.ensure(c, s.userId());
            wishlists.add(c, id, productId);
            return load(c, id);
        });
    }

    public ArrayList<ProductDto> remove(Session s, long productId) {
        return db.transaction(c -> {
            long id = wishlists.ensure(c, s.userId());
            wishlists.remove(c, id, productId);
            return load(c, id);
        });
    }

    private ArrayList<ProductDto> load(java.sql.Connection c, long wishlistId) throws java.sql.SQLException {
        List<Long> ids = wishlists.productIds(c, wishlistId);
        ArrayList<ProductDto> found = products.findAll(c, ids);
        ArrayList<ProductDto> ordered = new ArrayList<>();
        for (Long id : ids) {
            for (ProductDto p : found) if (p.id() == id) ordered.add(p);
        }
        return ordered;
    }
}
