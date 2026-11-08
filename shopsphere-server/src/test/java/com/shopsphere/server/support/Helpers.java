package com.shopsphere.server.support;

import com.shopsphere.common.dto.AdminDtos.InventoryDto;
import com.shopsphere.common.dto.AuthDtos.AddressDto;
import com.shopsphere.common.dto.AuthDtos.RegisterRequest;
import com.shopsphere.common.dto.CatalogDtos.ProductDto;
import com.shopsphere.common.dto.CatalogDtos.ProductQuery;
import com.shopsphere.common.dto.ShoppingDtos.CartUpdate;
import com.shopsphere.common.dto.ShoppingDtos.CheckoutRequest;
import com.shopsphere.common.dto.ShoppingDtos.CheckoutResult;
import com.shopsphere.common.protocol.Command;
import com.shopsphere.server.support.TestEnv.TestClient;
import java.util.ArrayList;
import java.util.UUID;

/** Shortcuts shared by the integration tests. */
public final class Helpers {

    public final TestEnv env;

    public Helpers(TestEnv env) {
        this.env = env;
    }

    public static Helpers fresh() {
        return new Helpers(TestEnv.fresh());
    }

    public TestClient customer() {
        TestClient c = env.client();
        c.login(TestEnv.CUSTOMER_EMAIL, TestEnv.CUSTOMER_PASSWORD);
        return c;
    }

    public TestClient admin() {
        TestClient c = env.client();
        c.login(TestEnv.ADMIN_EMAIL, TestEnv.ADMIN_PASSWORD);
        return c;
    }

    public TestClient owner() {
        TestClient c = env.client();
        c.login(TestEnv.OWNER_EMAIL, TestEnv.OWNER_PASSWORD);
        return c;
    }

    /** Register a new customer with an address and log in. */
    public TestClient newCustomer(String email) {
        TestClient c = env.client();
        c.ok(Command.REGISTER, new RegisterRequest(email, "Passw0rd!x", "Test", "User", null));
        c.login(email, "Passw0rd!x");
        c.ok(Command.ADD_ADDRESS, new AddressDto(null, "Test User", "1 Test Road", null, "Cape Town", "Western Cape", "8001", "South Africa", null));
        return c;
    }

    public long addressId(TestClient c) {
        ArrayList<AddressDto> list = c.ok(Command.LIST_ADDRESSES, null);
        return list.get(0).id();
    }

    public ProductDto product(TestClient c, String sku) {
        ArrayList<ProductDto> list = c.ok(Command.LIST_PRODUCTS, new ProductQuery(sku, null, true));
        return list.stream().filter(p -> p.sku().equals(sku)).findFirst().orElseThrow();
    }

    public int stock(String sku) {
        TestClient a = admin();
        ArrayList<InventoryDto> inv = a.ok(Command.LIST_INVENTORY, null);
        return inv.stream().filter(i -> i.sku().equals(sku)).findFirst().orElseThrow().quantity();
    }

    public void addToCart(TestClient c, String sku, int qty) {
        c.ok(Command.ADD_TO_CART, new CartUpdate(product(c, sku).id(), qty));
    }

    public CheckoutResult checkout(TestClient c, String card) {
        return c.ok(Command.CHECKOUT, new CheckoutRequest(addressId(c), card, UUID.randomUUID().toString()));
    }
}
