package com.shopsphere.server.service;

import com.shopsphere.common.dto.CatalogDtos.ProductDto;
import com.shopsphere.common.dto.CatalogDtos.ReviewDto;
import com.shopsphere.common.dto.CatalogDtos.ReviewRequest;
import com.shopsphere.common.protocol.ErrorCode;
import com.shopsphere.server.dao.OrderDao;
import com.shopsphere.server.dao.ProductDao;
import com.shopsphere.server.dao.ReviewDao;
import com.shopsphere.server.database.Database;
import com.shopsphere.server.security.Session;
import com.shopsphere.server.validation.Validator;
import java.util.ArrayList;

/**
 * Reviews are verified-purchase reviews. A customer may review a product only after an order that
 * contains it has been delivered, and only once per product.
 */
public class ReviewService {

    private final Database db;
    private final ReviewDao reviews;
    private final ProductDao products;
    private final OrderDao orders;

    public ReviewService(Database db, ReviewDao reviews, ProductDao products, OrderDao orders) {
        this.db = db;
        this.reviews = reviews;
        this.products = products;
        this.orders = orders;
    }

    public ArrayList<ReviewDto> list(long productId) {
        return db.read(c -> {
            if (products.find(c, productId).isEmpty()) throw ShopException.notFound("Product");
            return reviews.list(c, productId);
        });
    }

    public ReviewDto add(Session session, ReviewRequest r) {
        if (r == null) throw ShopException.invalid("Review details are required");
        int rating = Validator.rating(r.rating());
        String comment = Validator.optionalText(r.comment(), "Comment", 1000);
        try {
            return db.transaction(c -> {
                ProductDto p = products.find(c, r.productId()).orElseThrow(() -> ShopException.notFound("Product"));
                if (reviews.exists(c, session.userId(), p.id())) {
                    throw new ShopException(ErrorCode.CONFLICT, "You have already reviewed this product");
                }
                if (!orders.hasReceivedProduct(c, session.userId(), p.id())) {
                    throw ShopException.forbidden("You can only review products from an order that was delivered to you");
                }
                long id = reviews.insert(c, session.userId(), p.id(), rating, comment, true);
                return reviews.list(c, p.id()).stream().filter(x -> x.id() == id).findFirst().orElseThrow();
            });
        } catch (RuntimeException e) {
            if (Database.isUniqueViolation(e)) throw new ShopException(ErrorCode.CONFLICT, "You have already reviewed this product");
            throw e;
        }
    }
}
