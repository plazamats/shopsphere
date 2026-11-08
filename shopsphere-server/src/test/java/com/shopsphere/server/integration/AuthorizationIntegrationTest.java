package com.shopsphere.server.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shopsphere.common.dto.AdminDtos.AuditLogDto;
import com.shopsphere.common.dto.AdminDtos.AuditQuery;
import com.shopsphere.common.dto.AuthDtos.CreateAdminRequest;
import com.shopsphere.common.dto.AuthDtos.RoleChangeRequest;
import com.shopsphere.common.dto.AuthDtos.UserDto;
import com.shopsphere.common.dto.AuthDtos.UserStatusRequest;
import com.shopsphere.common.dto.ShoppingDtos.CheckoutResult;
import com.shopsphere.common.protocol.Command;
import com.shopsphere.common.protocol.ErrorCode;
import com.shopsphere.common.protocol.Role;
import com.shopsphere.common.protocol.UserStatus;
import com.shopsphere.server.support.Helpers;
import com.shopsphere.server.support.TestEnv;
import com.shopsphere.server.support.TestEnv.TestClient;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthorizationIntegrationTest {

    private Helpers h;

    @BeforeEach
    void setUp() {
        h = Helpers.fresh();
    }

    @Test
    void visitorsOnlySeePublicCommands() {
        TestClient v = h.env.client();
        v.ok(Command.LIST_PRODUCTS, null);
        v.ok(Command.LIST_CATEGORIES, null);
        assertEquals(ErrorCode.UNAUTHENTICATED, v.fails(Command.GET_CART, null));
        assertEquals(ErrorCode.UNAUTHENTICATED, v.fails(Command.GET_DASHBOARD, null));
    }

    @Test
    void customersCannotUseAnyAdminCommand() {
        TestClient c = h.customer();
        Command[] admin = {Command.GET_DASHBOARD, Command.LIST_USERS, Command.LIST_ALL_ORDERS, Command.LIST_INVENTORY,
                Command.LIST_PAYMENTS, Command.LIST_AUDIT_LOGS, Command.LIST_ADMINS, Command.LIST_SETTINGS, Command.GET_REPORT};
        for (Command cmd : admin) assertEquals(ErrorCode.FORBIDDEN, c.fails(cmd, null), cmd.name());
    }

    @Test
    void adminsCannotUseSuperAdminCommandsOrShop() {
        TestClient a = h.admin();
        assertEquals(ErrorCode.FORBIDDEN, a.fails(Command.LIST_ADMINS, null));
        assertEquals(ErrorCode.FORBIDDEN, a.fails(Command.LIST_SETTINGS, null));
        assertEquals(ErrorCode.FORBIDDEN, a.fails(Command.CREATE_ADMIN, new CreateAdminRequest("x@y.com", "Passw0rd!x", "A", "B")));
        assertEquals(ErrorCode.FORBIDDEN, a.fails(Command.GET_CART, null));
    }

    @Test
    void nobodyCanGrantThemselvesSuperAdmin() {
        TestClient a = h.admin();
        UserDto me = a.ok(Command.GET_PROFILE, null);
        assertEquals(ErrorCode.FORBIDDEN, a.fails(Command.SET_USER_ROLE, new RoleChangeRequest(me.id(), Role.SUPER_ADMIN)));
        TestClient owner = h.owner();
        ErrorCode code = owner.fails(Command.SET_USER_ROLE, new RoleChangeRequest(me.id(), Role.SUPER_ADMIN));
        assertTrue(code == ErrorCode.FORBIDDEN || code == ErrorCode.VALIDATION_FAILED, "got " + code);
        UserDto after = h.admin().ok(Command.GET_PROFILE, null);
        assertEquals(Role.ADMIN, after.role());
    }

    @Test
    void superAdminCanCreateAnAdminButNotAnotherSuperAdmin() {
        TestClient owner = h.owner();
        UserDto created = owner.ok(Command.CREATE_ADMIN, new CreateAdminRequest("second.admin@shopsphere.test", "Passw0rd!x", "Second", "Admin"));
        assertEquals(Role.ADMIN, created.role());
        TestClient second = h.env.client();
        second.login("second.admin@shopsphere.test", "Passw0rd!x");
        second.ok(Command.GET_DASHBOARD, null);
    }

    @Test
    void superAdminCannotSuspendOrDemoteThemselves() {
        TestClient owner = h.owner();
        UserDto me = owner.ok(Command.GET_PROFILE, null);
        assertEquals(ErrorCode.FORBIDDEN, owner.fails(Command.SET_USER_STATUS, new UserStatusRequest(me.id(), UserStatus.SUSPENDED)));
        assertEquals(ErrorCode.FORBIDDEN, owner.fails(Command.SET_USER_ROLE, new RoleChangeRequest(me.id(), Role.CUSTOMER)));
    }

    @Test
    void anAdminCannotSuspendAnotherAdminOrTheSuperAdmin() {
        TestClient owner = h.owner();
        UserDto ownerMe = owner.ok(Command.GET_PROFILE, null);
        TestClient admin = h.admin();
        assertEquals(ErrorCode.FORBIDDEN, admin.fails(Command.SET_USER_STATUS, new UserStatusRequest(ownerMe.id(), UserStatus.SUSPENDED)));
        owner.ok(Command.CREATE_ADMIN, new CreateAdminRequest("other.admin@shopsphere.test", "Passw0rd!x", "Other", "Admin"));
        ArrayList<UserDto> admins = owner.ok(Command.LIST_ADMINS, null);
        long otherId = admins.stream().filter(u -> u.email().startsWith("other.admin")).findFirst().orElseThrow().id();
        assertEquals(ErrorCode.FORBIDDEN, admin.fails(Command.SET_USER_STATUS, new UserStatusRequest(otherId, UserStatus.SUSPENDED)));
    }

    @Test
    void customersCannotSeeOrCancelOtherCustomersOrders() {
        TestClient alice = h.newCustomer("alice@example.com");
        h.addToCart(alice, "BOOK-001", 1);
        CheckoutResult placed = h.checkout(alice, TestEnv.GOOD_CARD);
        TestClient bob = h.newCustomer("bob@example.com");
        long orderId = placed.order().id();
        assertEquals(ErrorCode.NOT_FOUND, bob.fails(Command.GET_ORDER, orderId));
        assertEquals(ErrorCode.NOT_FOUND, bob.fails(Command.GET_RECEIPT, orderId));
        assertEquals(ErrorCode.NOT_FOUND, bob.fails(Command.CANCEL_ORDER, orderId));
        alice.ok(Command.GET_ORDER, orderId);
        h.admin().ok(Command.GET_ORDER, orderId);
    }

    @Test
    void customersCannotCheckoutWithSomeoneElsesAddress() {
        TestClient alice = h.newCustomer("alice2@example.com");
        long aliceAddress = h.addressId(alice);
        TestClient bob = h.newCustomer("bob2@example.com");
        h.addToCart(bob, "BOOK-001", 1);
        assertEquals(ErrorCode.VALIDATION_FAILED, bob.fails(Command.CHECKOUT,
                new com.shopsphere.common.dto.ShoppingDtos.CheckoutRequest(aliceAddress, TestEnv.GOOD_CARD, "key-aaaa-bbbb-1")));
    }

    @Test
    void auditLogHidesSecurityEventsFromAdmins() {
        TestClient c = h.env.client();
        for (int i = 0; i < 2; i++) {
            c.fails(Command.LOGIN, new com.shopsphere.common.dto.AuthDtos.LoginRequest(TestEnv.CUSTOMER_EMAIL, "Wrong#Pass1"));
        }
        ArrayList<AuditLogDto> forAdmin = h.admin().ok(Command.LIST_AUDIT_LOGS, new AuditQuery(200));
        ArrayList<AuditLogDto> forOwner = h.owner().ok(Command.LIST_AUDIT_LOGS, new AuditQuery(200));
        assertTrue(forAdmin.stream().noneMatch(a -> "SECURITY".equals(a.category())));
        assertTrue(forOwner.stream().anyMatch(a -> "SECURITY".equals(a.category())));
        assertFalse(forOwner.isEmpty());
    }

    @Test
    void adminActionsAreAudited() {
        TestClient owner = h.owner();
        owner.ok(Command.UPDATE_SETTING, new com.shopsphere.common.dto.AdminDtos.SettingDto("vat_rate", "0.10", null));
        ArrayList<AuditLogDto> log = owner.ok(Command.LIST_AUDIT_LOGS, new AuditQuery(50));
        assertTrue(log.stream().anyMatch(a -> "SETTING_CHANGED".equals(a.action()) && TestEnv.OWNER_EMAIL.equals(a.actorEmail())));
    }
}
