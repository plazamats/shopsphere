package com.shopsphere.common.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;

/** DTOs for categories, products and reviews. */
public final class CatalogDtos {

    private CatalogDtos() { }

    public record CategoryDto(Long id, String name, String description) implements Serializable {
        @Override public String toString() { return name; }
    }

    /** stock is the quantity on hand. Customers only need available, admins use the number. */
    public record ProductDto(long id, String sku, String name, String description, BigDecimal price,
                             Long categoryId, String categoryName, boolean active, int stock,
                             boolean available, ArrayList<String> imageUrls,
                             double averageRating, int reviewCount) implements Serializable { }

    /** Search text and category are optional. includeInactive only works for administrators. */
    public record ProductQuery(String search, Long categoryId, boolean includeInactive) implements Serializable { }

    /** id is null when creating. initialStock is only used when creating. */
    public record ProductSaveRequest(Long id, String sku, String name, String description, BigDecimal price,
                                     Long categoryId, boolean active, ArrayList<String> imageUrls,
                                     int initialStock) implements Serializable { }

    public record ReviewDto(long id, long productId, String reviewerName, int rating, String comment,
                            boolean verifiedPurchase, Instant createdAt) implements Serializable { }

    public record ReviewRequest(long productId, int rating, String comment) implements Serializable { }
}
