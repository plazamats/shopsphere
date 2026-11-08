package com.shopsphere.server.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.shopsphere.server.service.ShopException;
import com.shopsphere.server.validation.Validator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ValidatorTest {

    @Test
    void acceptsNormalEmailAndRejectsBadOnes() {
        assertEquals("a.b@example.com", Validator.email("a.b@example.com"));
        assertThrows(ShopException.class, () -> Validator.email("not-an-email"));
        assertThrows(ShopException.class, () -> Validator.email(null));
        assertThrows(ShopException.class, () -> Validator.email("a@b"));
        assertThrows(ShopException.class, () -> Validator.email("x@y.com'; DROP TABLE users; --"));
    }

    @Test
    void passwordPolicy() {
        Validator.password("Abcdef12", null);
        assertThrows(ShopException.class, () -> Validator.password("short1A", null));
        assertThrows(ShopException.class, () -> Validator.password("alllowercase1", null));
        assertThrows(ShopException.class, () -> Validator.password("NoDigitsHere", null));
        assertThrows(ShopException.class, () -> Validator.password("Same1234x", "same1234x"));
    }

    @Test
    void priceMustBePositiveAndBounded() {
        Validator.price(new BigDecimal("10.50"));
        assertThrows(ShopException.class, () -> Validator.price(new BigDecimal("0")));
        assertThrows(ShopException.class, () -> Validator.price(new BigDecimal("-1")));
        assertThrows(ShopException.class, () -> Validator.price(new BigDecimal("99999999")));
        assertThrows(ShopException.class, () -> Validator.price(null));
    }

    @Test
    void quantityLimits() {
        assertEquals(3, Validator.quantity(3, 20));
        assertThrows(ShopException.class, () -> Validator.quantity(0, 20));
        assertThrows(ShopException.class, () -> Validator.quantity(-4, 20));
        assertThrows(ShopException.class, () -> Validator.quantity(21, 20));
    }

    @Test
    void cardNumberUsesTheLuhnCheck() {
        assertEquals("4242424242424242", Validator.cardNumber("4242 4242 4242 4242"));
        assertThrows(ShopException.class, () -> Validator.cardNumber("4242 4242 4242 4241"));
        assertThrows(ShopException.class, () -> Validator.cardNumber("abcd"));
        assertThrows(ShopException.class, () -> Validator.cardNumber(null));
    }

    @Test
    void skuAndRatingAndImageReference() {
        Validator.sku("ELEC-001");
        assertThrows(ShopException.class, () -> Validator.sku("bad sku!"));
        assertEquals(5, Validator.rating(5));
        assertThrows(ShopException.class, () -> Validator.rating(6));
        assertThrows(ShopException.class, () -> Validator.rating(0));
        assertThrows(ShopException.class, () -> Validator.imageRef("javascript:alert(1)"));
    }

    @Test
    void idempotencyKeyShape() {
        Validator.idempotencyKey("abcd-1234-efgh");
        assertThrows(ShopException.class, () -> Validator.idempotencyKey("x"));
        assertThrows(ShopException.class, () -> Validator.idempotencyKey("has spaces in it!"));
    }
}
