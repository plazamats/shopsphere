package com.shopsphere.server.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shopsphere.common.dto.AdminDtos.SettingDto;
import com.shopsphere.common.dto.CatalogDtos.ProductDto;
import com.shopsphere.common.dto.CatalogDtos.ProductSaveRequest;
import com.shopsphere.common.dto.CatalogDtos.ReviewDto;
import com.shopsphere.common.dto.CatalogDtos.ReviewRequest;
import com.shopsphere.common.dto.ShoppingDtos.CartDto;
import com.shopsphere.common.dto.ShoppingDtos.CartUpdate;
import com.shopsphere.common.dto.ShoppingDtos.CheckoutRequest;
import com.shopsphere.common.dto.ShoppingDtos.CheckoutResult;
import com.shopsphere.common.dto.ShoppingDtos.OrderDto;
import com.shopsphere.common.dto.ShoppingDtos.OrderStatusRequest;
import com.shopsphere.common.dto.ShoppingDtos.PaymentDto;
import com.shopsphere.common.protocol.Command;
import com.shopsphere.common.protocol.ErrorCode;
import com.shopsphere.common.protocol.OrderStatus;
import com.shopsphere.common.protocol.PaymentStatus;
import com.shopsphere.server.support.Helpers;
import com.shopsphere.server.support.TestEnv;
import com.shopsphere.server.support.TestEnv.TestClient;
import java.math.BigDecimal;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderFlowIntegrationTest {

    private Helpers h;

    @BeforeEach
    void setUp() {
        h = Helpers.fresh();
    }

    @Test
    void cartPricesAreCalculatedByTheServer() {
        TestClient c = h.newCustomer("cart1@example.com");
        h.addToCart(c, "BOOK-001", 2);
        CartDto cart = c.ok(Command.GET_CART, null);
        assertEquals(1, cart.items().size());
        assertEquals(new BigDecimal("1798.00"), cart.pricing().subtotal());
        assertEquals(new BigDecimal("89.90"), cart.pricing().discount());
        assertTrue(cart.checkoutReady());
        h.addToCart(c, "BOOK-001", 1);
        assertEquals(3, ((CartDto) c.ok(Command.GET_CART, null)).items().get(0).quantity());
    }

    @Test
    void cartRefusesBadQuantitiesAndTooMuchStock() {
        TestClient c = h.newCustomer("cart2@example.com");
        long id = h.product(c, "BOOK-001").id();
        assertEquals(ErrorCode.VALIDATION_FAILED, c.fails(Command.ADD_TO_CART, new CartUpdate(id, 0)));
        assertEquals(ErrorCode.VALIDATION_FAILED, c.fails(Command.ADD_TO_CART, new CartUpdate(id, -5)));
        assertEquals(ErrorCode.VALIDATION_FAILED, c.fails(Command.ADD_TO_CART, new CartUpdate(id, 21)));
        assertEquals(ErrorCode.NOT_FOUND, c.fails(Command.ADD_TO_CART, new CartUpdate(999999, 1)));
        long scarce = h.product(c, "ELEC-002").id();
        assertEquals(ErrorCode.OUT_OF_STOCK, c.fails(Command.ADD_TO_CART, new CartUpdate(scarce, 4)));
        c.ok(Command.ADD_TO_CART, new CartUpdate(scarce, 3));
        assertEquals(ErrorCode.OUT_OF_STOCK, c.fails(Command.ADD_TO_CART, new CartUpdate(scarce, 1)));
    }

    @Test
    void cartItemsCanBeChangedAndRemoved() {
        TestClient c = h.newCustomer("cart3@example.com");
        h.addToCart(c, "BOOK-001", 1);
        long id = h.product(c, "BOOK-001").id();
        CartDto cart = c.ok(Command.UPDATE_CART_ITEM, new CartUpdate(id, 4));
        assertEquals(4, cart.items().get(0).quantity());
        cart = c.ok(Command.REMOVE_CART_ITEM, id);
        assertTrue(cart.items().isEmpty());
        assertFalse(cart.checkoutReady());
    }

    @Test
    void successfulCheckoutTakesStockCreatesTheOrderAndEmptiesTheCart() {
        TestClient c = h.newCustomer("buy1@example.com");
        int before = h.stock("BOOK-001");
        h.addToCart(c, "BOOK-001", 2);
        CheckoutResult r = h.checkout(c, TestEnv.GOOD_CARD);
        assertTrue(r.paymentSucceeded());
        OrderDto o = r.order();
        assertEquals(OrderStatus.PAID, o.status());
        assertTrue(o.orderNumber().matches("SS-\\d{8}-\\d{6}"), o.orderNumber());
        assertEquals(new BigDecimal("1798.00"), o.pricing().subtotal());
        assertEquals(1, o.items().size());
        assertEquals("BOOK-001", o.items().get(0).sku());
        assertEquals(PaymentStatus.PAID, o.payment().status());
        assertTrue(o.payment().reference().startsWith("SIM-"));
        assertTrue(o.payment().simulated());
        assertEquals(before - 2, h.stock("BOOK-001"));
        assertTrue(((CartDto) c.ok(Command.GET_CART, null)).items().isEmpty());
    }

    @Test
    void orderItemsKeepTheirPriceWhenTheProductPriceChanges() {
        TestClient c = h.newCustomer("buy2@example.com");
        h.addToCart(c, "BOOK-001", 1);
        OrderDto placed = h.checkout(c, TestEnv.GOOD_CARD).order();
        TestClient admin = h.admin();
        ProductDto p = h.product(admin, "BOOK-001");
        admin.ok(Command.SAVE_PRODUCT, new ProductSaveRequest(p.id(), p.sku(), "Renamed book", p.description(), new BigDecimal("1.00"),
                p.categoryId(), true, p.imageUrls(), 0));
        OrderDto again = c.ok(Command.GET_ORDER, placed.id());
        assertEquals(new BigDecimal("899.00"), again.items().get(0).unitPrice());
        assertEquals(placed.items().get(0).name(), again.items().get(0).name());
        assertEquals(placed.pricing().total(), again.pricing().total());
    }

    @Test
    void declinedPaymentRestoresStockKeepsTheCartAndRecordsTheFailure() {
        TestClient c = h.newCustomer("buy3@example.com");
        int before = h.stock("BOOK-001");
        h.addToCart(c, "BOOK-001", 2);
        CheckoutResult r = h.checkout(c, TestEnv.DECLINED_CARD);
        assertFalse(r.paymentSucceeded());
        assertEquals(OrderStatus.PAYMENT_FAILED, r.order().status());
        assertEquals(PaymentStatus.FAILED, r.order().payment().status());
        assertEquals(before, h.stock("BOOK-001"));
        assertEquals(1, ((CartDto) c.ok(Command.GET_CART, null)).items().size());
        // the customer can try again with a working card
        CheckoutResult second = h.checkout(c, TestEnv.GOOD_CARD);
        assertTrue(second.paymentSucceeded());
        assertEquals(before - 2, h.stock("BOOK-001"));
    }

    @Test
    void insufficientFundsCardIsAlsoDeclined() {
        TestClient c = h.newCustomer("buy4@example.com");
        h.addToCart(c, "BOOK-001", 1);
        CheckoutResult r = h.checkout(c, "4000 0000 0000 9995");
        assertFalse(r.paymentSucceeded());
        assertTrue(r.message().toLowerCase().contains("insufficient"));
    }

    @Test
    void repeatingACheckoutRequestNeverCreatesASecondOrder() {
        TestClient c = h.newCustomer("buy5@example.com");
        int before = h.stock("BOOK-001");
        h.addToCart(c, "BOOK-001", 1);
        CheckoutRequest req = new CheckoutRequest(h.addressId(c), TestEnv.GOOD_CARD, "same-key-12345");
        CheckoutResult first = c.ok(Command.CHECKOUT, req);
        CheckoutResult second = c.ok(Command.CHECKOUT, req);
        assertEquals(first.order().id(), second.order().id());
        assertEquals(first.order().orderNumber(), second.order().orderNumber());
        assertEquals(before - 1, h.stock("BOOK-001"));
        ArrayList<OrderDto> mine = c.ok(Command.LIST_MY_ORDERS, null);
        assertEquals(1, mine.size());
    }

    @Test
    void checkoutRefusesEmptyCartAndBadCards() {
        TestClient c = h.newCustomer("buy6@example.com");
        long address = h.addressId(c);
        assertEquals(ErrorCode.VALIDATION_FAILED, c.fails(Command.CHECKOUT, new CheckoutRequest(address, TestEnv.GOOD_CARD, "empty-cart-key1")));
        h.addToCart(c, "BOOK-001", 1);
        assertEquals(ErrorCode.VALIDATION_FAILED, c.fails(Command.CHECKOUT, new CheckoutRequest(address, "1234 5678 9012 3456", "bad-card-key-1")));
        assertEquals(ErrorCode.VALIDATION_FAILED, c.fails(Command.CHECKOUT, new CheckoutRequest(address, "4242 4242 4242 4242", "x")));
        assertTrue(((ArrayList<?>) c.ok(Command.LIST_MY_ORDERS, null)).isEmpty());
    }

    @Test
    void cardNumbersAreNeverStoredInFull() throws Exception {
        TestClient c = h.newCustomer("buy7@example.com");
        h.addToCart(c, "BOOK-001", 1);
        h.checkout(c, TestEnv.GOOD_CARD);
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(h.env.config().dbUrl(), h.env.config().dbUser(), h.env.config().dbPassword());
             java.sql.Statement st = conn.createStatement();
             java.sql.ResultSet rs = st.executeQuery("SELECT card_last4 FROM payments")) {
            assertTrue(rs.next());
            assertEquals("4242", rs.getString(1));
        }
    }

    @Test
    void customerCancelsAPaidOrderAndStockAndPaymentAreReversed() {
        TestClient c = h.newCustomer("cancel1@example.com");
        int before = h.stock("BOOK-001");
        h.addToCart(c, "BOOK-001", 3);
        OrderDto o = h.checkout(c, TestEnv.GOOD_CARD).order();
        assertEquals(before - 3, h.stock("BOOK-001"));
        OrderDto cancelled = c.ok(Command.CANCEL_ORDER, o.id());
        assertEquals(OrderStatus.CANCELLED, cancelled.status());
        assertEquals(PaymentStatus.REFUNDED, cancelled.payment().status());
        assertEquals(before, h.stock("BOOK-001"));
        assertEquals(ErrorCode.CONFLICT, c.fails(Command.CANCEL_ORDER, o.id()));
        assertEquals(before, h.stock("BOOK-001"));
    }

    @Test
    void customersCannotCancelOnceTheOrderIsBeingProcessed() {
        TestClient c = h.newCustomer("cancel2@example.com");
        h.addToCart(c, "BOOK-001", 1);
        OrderDto o = h.checkout(c, TestEnv.GOOD_CARD).order();
        h.admin().ok(Command.UPDATE_ORDER_STATUS, new OrderStatusRequest(o.id(), OrderStatus.PROCESSING));
        assertEquals(ErrorCode.CONFLICT, c.fails(Command.CANCEL_ORDER, o.id()));
    }

    @Test
    void adminMovesAnOrderThroughTheLifecycleAndInvalidJumpsAreRefused() {
        TestClient c = h.newCustomer("flow1@example.com");
        h.addToCart(c, "BOOK-001", 1);
        OrderDto o = h.checkout(c, TestEnv.GOOD_CARD).order();
        TestClient admin = h.admin();
        assertEquals(ErrorCode.CONFLICT, admin.fails(Command.UPDATE_ORDER_STATUS, new OrderStatusRequest(o.id(), OrderStatus.DELIVERED)));
        for (OrderStatus s : new OrderStatus[] {OrderStatus.PROCESSING, OrderStatus.SHIPPED, OrderStatus.DELIVERED}) {
            OrderDto updated = admin.ok(Command.UPDATE_ORDER_STATUS, new OrderStatusRequest(o.id(), s));
            assertEquals(s, updated.status());
        }
        assertEquals(ErrorCode.CONFLICT, admin.fails(Command.UPDATE_ORDER_STATUS, new OrderStatusRequest(o.id(), OrderStatus.CANCELLED)));
        assertEquals(OrderStatus.DELIVERED, ((OrderDto) c.ok(Command.GET_ORDER, o.id())).status());
    }

    @Test
    void receiptIsOnlyForPaidOrdersAndMentionsTheSimulation() {
        TestClient c = h.newCustomer("receipt1@example.com");
        h.addToCart(c, "BOOK-001", 1);
        OrderDto o = h.checkout(c, TestEnv.GOOD_CARD).order();
        String receipt = c.ok(Command.GET_RECEIPT, o.id());
        assertTrue(receipt.contains(o.orderNumber()));
        assertTrue(receipt.toLowerCase().contains("simulated"));
        h.addToCart(c, "BOOK-001", 1);
        OrderDto failed = h.checkout(c, TestEnv.DECLINED_CARD).order();
        assertEquals(ErrorCode.CONFLICT, c.fails(Command.GET_RECEIPT, failed.id()));
    }

    @Test
    void reviewsNeedADeliveredOrderAndAreLimitedToOnePerProduct() {
        TestClient c = h.newCustomer("review1@example.com");
        long productId = h.product(c, "BOOK-001").id();
        assertEquals(ErrorCode.FORBIDDEN, c.fails(Command.ADD_REVIEW, new ReviewRequest(productId, 5, "Great")));
        h.addToCart(c, "BOOK-001", 1);
        OrderDto o = h.checkout(c, TestEnv.GOOD_CARD).order();
        assertEquals(ErrorCode.FORBIDDEN, c.fails(Command.ADD_REVIEW, new ReviewRequest(productId, 5, "Not delivered yet")));
        TestClient admin = h.admin();
        for (OrderStatus s : new OrderStatus[] {OrderStatus.PROCESSING, OrderStatus.SHIPPED, OrderStatus.DELIVERED}) {
            admin.ok(Command.UPDATE_ORDER_STATUS, new OrderStatusRequest(o.id(), s));
        }
        assertEquals(ErrorCode.VALIDATION_FAILED, c.fails(Command.ADD_REVIEW, new ReviewRequest(productId, 9, "Bad rating")));
        ReviewDto r = c.ok(Command.ADD_REVIEW, new ReviewRequest(productId, 4, "Very good"));
        assertTrue(r.verifiedPurchase());
        assertEquals(ErrorCode.CONFLICT, c.fails(Command.ADD_REVIEW, new ReviewRequest(productId, 5, "Again")));
        ArrayList<ReviewDto> list = c.ok(Command.LIST_REVIEWS, productId);
        assertEquals(1, list.size());
        ProductDto p = c.ok(Command.GET_PRODUCT, productId);
        assertEquals(1, p.reviewCount());
    }

    @Test
    void wishlistAddRemove() {
        TestClient c = h.newCustomer("wish1@example.com");
        long id = h.product(c, "BOOK-001").id();
        ArrayList<ProductDto> list = c.ok(Command.ADD_WISHLIST, id);
        assertEquals(1, list.size());
        list = c.ok(Command.ADD_WISHLIST, id);
        assertEquals(1, list.size());
        list = c.ok(Command.REMOVE_WISHLIST, id);
        assertTrue(list.isEmpty());
        assertNotNull(list);
    }

    @Test
    void changingASystemSettingChangesThePricing() {
        TestClient c = h.newCustomer("settings1@example.com");
        h.addToCart(c, "CLOT-001", 1);
        BigDecimal before = ((CartDto) c.ok(Command.GET_CART, null)).pricing().tax();
        h.owner().ok(Command.UPDATE_SETTING, new SettingDto("vat_rate", "0.10", null));
        BigDecimal after = ((CartDto) c.ok(Command.GET_CART, null)).pricing().tax();
        assertEquals(new BigDecimal("29.85"), before);
        assertEquals(new BigDecimal("19.90"), after);
        assertEquals(ErrorCode.VALIDATION_FAILED, h.owner().fails(Command.UPDATE_SETTING, new SettingDto("vat_rate", "abc", null)));
        assertEquals(ErrorCode.VALIDATION_FAILED, h.owner().fails(Command.UPDATE_SETTING, new SettingDto("vat_rate", "5", null)));
    }

    @Test
    void reportsAndDashboardWork() {
        TestClient c = h.newCustomer("report1@example.com");
        h.addToCart(c, "BOOK-001", 1);
        h.checkout(c, TestEnv.GOOD_CARD);
        TestClient admin = h.admin();
        var dash = admin.ok(Command.GET_DASHBOARD, null);
        assertNotNull(dash);
        for (var t : com.shopsphere.common.protocol.ReportType.values()) {
            com.shopsphere.common.dto.AdminDtos.ReportDto r = admin.ok(Command.GET_REPORT, t);
            assertNotNull(r.title());
        }
        ArrayList<PaymentDto> payments = admin.ok(Command.LIST_PAYMENTS, null);
        assertEquals(1, payments.size());
    }
}
