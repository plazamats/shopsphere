package com.shopsphere.server.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shopsphere.common.dto.AuthDtos.ChangePasswordRequest;
import com.shopsphere.common.dto.AuthDtos.LoginRequest;
import com.shopsphere.common.dto.AuthDtos.RegisterRequest;
import com.shopsphere.common.dto.AuthDtos.UpdateProfileRequest;
import com.shopsphere.common.dto.AuthDtos.UserDto;
import com.shopsphere.common.dto.AuthDtos.UserStatusRequest;
import com.shopsphere.common.protocol.Command;
import com.shopsphere.common.protocol.ErrorCode;
import com.shopsphere.common.protocol.Role;
import com.shopsphere.common.protocol.UserStatus;
import com.shopsphere.server.support.Helpers;
import com.shopsphere.server.support.TestEnv;
import com.shopsphere.server.support.TestEnv.TestClient;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthIntegrationTest {

    private Helpers h;

    @BeforeEach
    void setUp() {
        h = Helpers.fresh();
    }

    @Test
    void registerThenLogin() {
        TestClient c = h.env.client();
        UserDto u = c.ok(Command.REGISTER, new RegisterRequest("new.user@example.com", "Passw0rd!x", "New", "User", "0821112222"));
        assertEquals(Role.CUSTOMER, u.role());
        UserDto loggedIn = c.login("new.user@example.com", "Passw0rd!x");
        assertEquals("new.user@example.com", loggedIn.email());
        assertEquals(Role.CUSTOMER, loggedIn.role());
    }

    @Test
    void registerCannotChooseARole() {
        // the request has no role field at all, so a client cannot ask for one
        TestClient c = h.env.client();
        UserDto u = c.ok(Command.REGISTER, new RegisterRequest("plain@example.com", "Passw0rd!x", "Plain", "User", null));
        assertEquals(Role.CUSTOMER, u.role());
    }

    @Test
    void duplicateEmailIsRefusedIgnoringCase() {
        TestClient c = h.env.client();
        c.ok(Command.REGISTER, new RegisterRequest("dup@example.com", "Passw0rd!x", "A", "B", null));
        assertEquals(ErrorCode.CONFLICT, c.fails(Command.REGISTER, new RegisterRequest("DUP@example.com", "Passw0rd!x", "A", "B", null)));
    }

    @Test
    void weakPasswordAndBadEmailAreRefused() {
        TestClient c = h.env.client();
        assertEquals(ErrorCode.VALIDATION_FAILED, c.fails(Command.REGISTER, new RegisterRequest("weak@example.com", "password", "A", "B", null)));
        assertEquals(ErrorCode.VALIDATION_FAILED, c.fails(Command.REGISTER, new RegisterRequest("nope", "Passw0rd!x", "A", "B", null)));
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameAnswer() {
        TestClient c = h.env.client();
        ErrorCode wrong = c.fails(Command.LOGIN, new LoginRequest(TestEnv.CUSTOMER_EMAIL, "Wrong#Pass1"));
        ErrorCode unknown = c.fails(Command.LOGIN, new LoginRequest("nobody@example.com", "Wrong#Pass1"));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, wrong);
        assertEquals(wrong, unknown);
    }

    @Test
    void accountLocksAfterRepeatedFailuresEvenForTheRightPassword() {
        TestClient c = h.env.client();
        for (int i = 0; i < 5; i++) c.fails(Command.LOGIN, new LoginRequest(TestEnv.CUSTOMER_EMAIL, "Wrong#Pass1"));
        assertEquals(ErrorCode.ACCOUNT_LOCKED, c.fails(Command.LOGIN, new LoginRequest(TestEnv.CUSTOMER_EMAIL, TestEnv.CUSTOMER_PASSWORD)));
    }

    @Test
    void passwordsAreStoredHashedNeverAsPlainText() throws Exception {
        try (Connection c = java.sql.DriverManager.getConnection(h.env.config().dbUrl(), h.env.config().dbUser(), h.env.config().dbPassword());
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT password_hash FROM users")) {
            int rows = 0;
            while (rs.next()) {
                rows++;
                String hash = rs.getString(1);
                assertTrue(hash.startsWith("pbkdf2_sha256$"));
                assertFalse(hash.contains(TestEnv.CUSTOMER_PASSWORD));
                assertFalse(hash.contains(TestEnv.OWNER_PASSWORD));
            }
            assertTrue(rows >= 3);
        }
    }

    @Test
    void suspendingAnAccountEndsItsSessionAndBlocksLogin() {
        TestClient customer = h.customer();
        TestClient admin = h.admin();
        UserDto me = customer.ok(Command.GET_PROFILE, null);
        admin.ok(Command.SET_USER_STATUS, new UserStatusRequest(me.id(), UserStatus.SUSPENDED));
        assertEquals(ErrorCode.UNAUTHENTICATED, customer.fails(Command.GET_PROFILE, null));
        assertEquals(ErrorCode.ACCOUNT_SUSPENDED, h.env.client().fails(Command.LOGIN, new LoginRequest(TestEnv.CUSTOMER_EMAIL, TestEnv.CUSTOMER_PASSWORD)));
        admin.ok(Command.SET_USER_STATUS, new UserStatusRequest(me.id(), UserStatus.ACTIVE));
        h.customer();
    }

    @Test
    void logoutInvalidatesTheToken() {
        TestClient c = h.customer();
        String token = c.token();
        c.ok(Command.LOGOUT, null);
        c.setToken(token);
        assertEquals(ErrorCode.UNAUTHENTICATED, c.fails(Command.GET_PROFILE, null));
    }

    @Test
    void aTokenCannotBeUsedFromAnotherConnection() {
        TestClient owner = h.customer();
        TestClient thief = h.env.client();
        thief.setToken(owner.token());
        assertEquals(ErrorCode.UNAUTHENTICATED, thief.fails(Command.GET_PROFILE, null));
    }

    @Test
    void changePasswordRequiresTheCurrentOne() {
        TestClient c = h.customer();
        assertEquals(ErrorCode.INVALID_CREDENTIALS, c.fails(Command.CHANGE_PASSWORD,
                new ChangePasswordRequest("Wrong#Pass1", "NewPassw0rd!")));
        c.ok(Command.CHANGE_PASSWORD, new ChangePasswordRequest(TestEnv.CUSTOMER_PASSWORD, "NewPassw0rd!"));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, h.env.client().fails(Command.LOGIN, new LoginRequest(TestEnv.CUSTOMER_EMAIL, TestEnv.CUSTOMER_PASSWORD)));
        h.env.client().login(TestEnv.CUSTOMER_EMAIL, "NewPassw0rd!");
    }

    @Test
    void profileCanBeUpdated() {
        TestClient c = h.customer();
        UserDto u = c.ok(Command.UPDATE_PROFILE, new UpdateProfileRequest("Changed", "Name", "0831234567"));
        assertEquals("Changed", u.firstName());
        assertEquals("0831234567", u.phone());
    }

    @Test
    void sqlInjectionInLoginAndRegisterIsHarmless() throws Exception {
        TestClient c = h.env.client();
        String[] evil = {"' OR '1'='1", "admin@shopsphere.test' --", "'; DROP TABLE users; --", "\" OR \"\"=\""};
        for (String s : evil) {
            ErrorCode code = c.fails(Command.LOGIN, new LoginRequest(s, s));
            assertTrue(code == ErrorCode.INVALID_CREDENTIALS || code == ErrorCode.VALIDATION_FAILED, "got " + code);
            assertEquals(ErrorCode.VALIDATION_FAILED, c.fails(Command.REGISTER, new RegisterRequest(s, "Passw0rd!x", "A", "B", null)));
        }
        TestClient owner = h.owner();
        ArrayList<UserDto> admins = owner.ok(Command.LIST_ADMINS, null);
        assertTrue(admins.size() >= 2, "users table must still exist and hold the seeded admins");
    }

    @Test
    void errorMessagesDoNotLeakInternals() {
        TestClient c = h.env.client();
        String msg = c.send(Command.LOGIN, new LoginRequest("x@y.com", "whatever")).message();
        assertFalse(msg.toLowerCase().contains("sql"));
        assertFalse(msg.toLowerCase().contains("exception"));
    }
}
