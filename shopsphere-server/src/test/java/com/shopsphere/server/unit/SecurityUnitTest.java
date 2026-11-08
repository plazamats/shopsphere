package com.shopsphere.server.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shopsphere.common.protocol.Command;
import com.shopsphere.common.protocol.ErrorCode;
import com.shopsphere.common.protocol.OrderStatus;
import com.shopsphere.common.protocol.Role;
import com.shopsphere.server.security.CommandPolicy;
import com.shopsphere.server.security.Pbkdf2PasswordHasher;
import com.shopsphere.server.security.Session;
import com.shopsphere.server.security.SessionManager;
import com.shopsphere.server.service.ShopException;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class SecurityUnitTest {

    @Test
    void hashesAreSaltedAndVerify() {
        Pbkdf2PasswordHasher h = new Pbkdf2PasswordHasher(1000);
        String a = h.hash("Secret123");
        String b = h.hash("Secret123");
        assertNotEquals(a, b);
        assertFalse(a.contains("Secret123"));
        assertTrue(h.verify("Secret123", a));
        assertFalse(h.verify("secret123", a));
        assertFalse(h.verify("Secret123", "garbage"));
    }

    @Test
    void sessionIsBoundToItsConnection() {
        SessionManager m = new SessionManager(30);
        Session s = m.create(1, "a@b.com", Role.CUSTOMER, 7);
        assertTrue(m.resolve(s.token(), 7).isPresent());
        assertFalse(m.resolve(s.token(), 8).isPresent());
        assertFalse(m.resolve("made-up-token", 7).isPresent());
        assertFalse(m.resolve(null, 7).isPresent());
    }

    @Test
    void sessionExpiresWhenIdle() {
        AtomicLong now = new AtomicLong(0);
        SessionManager m = new SessionManager(30, now::get);
        Session s = m.create(1, "a@b.com", Role.CUSTOMER, 7);
        now.set(29 * 60_000L);
        assertTrue(m.resolve(s.token(), 7).isPresent());
        now.set(29 * 60_000L + 31 * 60_000L);
        assertFalse(m.resolve(s.token(), 7).isPresent());
    }

    @Test
    void disconnectAndSuspendEndSessions() {
        SessionManager m = new SessionManager(30);
        Session a = m.create(1, "a@b.com", Role.CUSTOMER, 7);
        Session b = m.create(2, "c@d.com", Role.CUSTOMER, 8);
        m.invalidateConnection(7);
        assertFalse(m.resolve(a.token(), 7).isPresent());
        assertTrue(m.resolve(b.token(), 8).isPresent());
        m.invalidateUser(2);
        assertFalse(m.resolve(b.token(), 8).isPresent());
    }

    @Test
    void everyCommandHasAnExplicitPolicyRule() {
        for (Command c : Command.values()) assertTrue(CommandPolicy.hasRule(c), "no rule for " + c);
    }

    @Test
    void adminCommandsAreRefusedForCustomersAndVisitors() {
        Session customer = new SessionManager(30).create(1, "c@x.com", Role.CUSTOMER, 1);
        ShopException e1 = assertThrows(ShopException.class, () -> CommandPolicy.check(Command.GET_DASHBOARD, customer));
        assertEquals(ErrorCode.FORBIDDEN, e1.code());
        ShopException e2 = assertThrows(ShopException.class, () -> CommandPolicy.check(Command.GET_CART, null));
        assertEquals(ErrorCode.UNAUTHENTICATED, e2.code());
        CommandPolicy.check(Command.LIST_PRODUCTS, null);
    }

    @Test
    void roleBoundaries() {
        SessionManager m = new SessionManager(30);
        Session admin = m.create(2, "a@x.com", Role.ADMIN, 2);
        Session owner = m.create(3, "o@x.com", Role.SUPER_ADMIN, 3);
        CommandPolicy.check(Command.SAVE_PRODUCT, admin);
        assertThrows(ShopException.class, () -> CommandPolicy.check(Command.CREATE_ADMIN, admin));
        assertThrows(ShopException.class, () -> CommandPolicy.check(Command.UPDATE_SETTING, admin));
        assertThrows(ShopException.class, () -> CommandPolicy.check(Command.ADD_TO_CART, admin));
        CommandPolicy.check(Command.CREATE_ADMIN, owner);
    }

    @Test
    void orderStatusTransitions() {
        assertTrue(OrderStatus.PAID.canTransitionTo(OrderStatus.PROCESSING));
        assertTrue(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.DELIVERED));
        assertFalse(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.CANCELLED));
        assertFalse(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.PAID));
        assertFalse(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.CANCELLED));
        assertTrue(OrderStatus.PAID.customerMayCancel());
        assertFalse(OrderStatus.PROCESSING.customerMayCancel());
    }
}
