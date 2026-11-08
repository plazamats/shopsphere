package com.shopsphere.server.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shopsphere.common.dto.CatalogDtos.CategoryDto;
import com.shopsphere.common.dto.CatalogDtos.ProductSaveRequest;
import com.shopsphere.common.dto.ShoppingDtos.CheckoutRequest;
import com.shopsphere.common.dto.ShoppingDtos.CheckoutResult;
import com.shopsphere.common.dto.ShoppingDtos.OrderDto;
import com.shopsphere.common.protocol.Command;
import com.shopsphere.common.protocol.Response;
import com.shopsphere.server.support.Helpers;
import com.shopsphere.server.support.TestEnv;
import com.shopsphere.server.support.TestEnv.TestClient;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConcurrencyIntegrationTest {

    private Helpers h;

    @BeforeEach
    void setUp() {
        h = Helpers.fresh();
    }

    private void createScarceProduct(String sku, int stock) {
        TestClient admin = h.admin();
        ArrayList<CategoryDto> cats = admin.ok(Command.LIST_CATEGORIES, null);
        admin.ok(Command.SAVE_PRODUCT, new ProductSaveRequest(null, sku, "Scarce item", "Limited", new BigDecimal("100.00"),
                cats.get(0).id(), true, new ArrayList<>(), stock));
    }

    @Test
    void tenCustomersFightForThreeUnitsAndExactlyThreeWin() throws Exception {
        createScarceProduct("RACE-001", 3);
        int buyers = 10;
        List<TestClient> clients = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            TestClient c = h.newCustomer("racer" + i + "@example.com");
            h.addToCart(c, "RACE-001", 1);
            clients.add(c);
        }
        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger paid = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        List<Future<?>> done = new ArrayList<>();
        for (TestClient c : clients) {
            done.add(pool.submit(() -> {
                start.await();
                Response r = c.send(Command.CHECKOUT, new CheckoutRequest(h.addressId(c), TestEnv.GOOD_CARD, java.util.UUID.randomUUID().toString()));
                if (r.ok() && ((CheckoutResult) r.data()).paymentSucceeded()) paid.incrementAndGet();
                else refused.incrementAndGet();
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : done) f.get(60, TimeUnit.SECONDS);
        pool.shutdownNow();
        assertEquals(3, paid.get(), "exactly the available units can be sold");
        assertEquals(buyers - 3, refused.get());
        assertEquals(0, h.stock("RACE-001"), "stock must end at zero, never negative");
    }

    @Test
    void doubleClickingPayOnTheSameRequestMakesOneOrder() throws Exception {
        createScarceProduct("RACE-002", 5);
        TestClient c = h.newCustomer("doubleclick@example.com");
        h.addToCart(c, "RACE-002", 1);
        long address = h.addressId(c);
        CheckoutRequest req = new CheckoutRequest(address, TestEnv.GOOD_CARD, "double-click-key-1");
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Response>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return c.send(Command.CHECKOUT, req);
            }));
        }
        start.countDown();
        java.util.Set<Long> orderIds = new java.util.HashSet<>();
        for (Future<Response> f : results) {
            Response r = f.get(60, TimeUnit.SECONDS);
            assertTrue(r.ok(), "every repeat should get the original order, got " + r.code() + " " + r.message());
            orderIds.add(((CheckoutResult) r.data()).order().id());
        }
        pool.shutdownNow();
        assertEquals(1, orderIds.size());
        assertEquals(4, h.stock("RACE-002"));
        ArrayList<OrderDto> orders = c.ok(Command.LIST_MY_ORDERS, null);
        assertEquals(1, orders.size());
    }
}
