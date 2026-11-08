package com.shopsphere.server.service;

import com.shopsphere.common.dto.AuthDtos.AddressDto;
import com.shopsphere.common.dto.AuthDtos.ChangePasswordRequest;
import com.shopsphere.common.dto.AuthDtos.LoginRequest;
import com.shopsphere.common.dto.AuthDtos.LoginResult;
import com.shopsphere.common.dto.AuthDtos.RegisterRequest;
import com.shopsphere.common.dto.AuthDtos.UpdateProfileRequest;
import com.shopsphere.common.dto.AuthDtos.UserDto;
import com.shopsphere.common.protocol.ErrorCode;
import com.shopsphere.common.protocol.Role;
import com.shopsphere.common.protocol.UserStatus;
import com.shopsphere.server.config.ServerConfig;
import com.shopsphere.server.dao.AddressDao;
import com.shopsphere.server.dao.UserDao;
import com.shopsphere.server.database.Database;
import com.shopsphere.server.model.UserAccount;
import com.shopsphere.server.security.PasswordHasher;
import com.shopsphere.server.security.Session;
import com.shopsphere.server.security.SessionManager;
import com.shopsphere.server.validation.Validator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

/** Registration, login, logout, profile, password and addresses. */
public class AuthService {

    private final Database db;
    private final UserDao users;
    private final AddressDao addresses;
    private final PasswordHasher hasher;
    private final SessionManager sessions;
    private final AuditService audit;
    private final ServerConfig config;
    /** Used to spend the same time when the email is unknown, so timing does not reveal which emails exist. */
    private final String dummyHash;

    public AuthService(Database db, UserDao users, AddressDao addresses, PasswordHasher hasher,
                       SessionManager sessions, AuditService audit, ServerConfig config) {
        this.db = db;
        this.users = users;
        this.addresses = addresses;
        this.hasher = hasher;
        this.sessions = sessions;
        this.audit = audit;
        this.config = config;
        this.dummyHash = hasher.hash("not-a-real-password");
    }

    /** What happened during a login attempt. It is decided inside the transaction and acted on after the commit. */
    private record Outcome(UserAccount user, ErrorCode error, String message) { }

    public UserDto register(RegisterRequest r) {
        if (r == null) throw ShopException.invalid("Registration details are required");
        String email = Validator.email(r.email());
        Validator.password(r.password(), email);
        String first = Validator.personName(r.firstName(), "First name");
        String last = Validator.personName(r.lastName(), "Last name");
        String phone = Validator.phone(r.phone());
        String hash = hasher.hash(r.password());
        try {
            return db.transaction(c -> {
                if (users.findByEmail(c, email).isPresent()) {
                    throw new ShopException(ErrorCode.CONFLICT, "An account with this email already exists");
                }
                long id = users.insert(c, email, first, last, phone, hash, Role.CUSTOMER);
                return users.findById(c, id).orElseThrow().toDto();
            });
        } catch (RuntimeException e) {
            if (Database.isUniqueViolation(e)) {
                throw new ShopException(ErrorCode.CONFLICT, "An account with this email already exists");
            }
            throw e;
        }
    }

    public LoginResult login(LoginRequest r, long connectionId) {
        if (r == null || r.email() == null || r.password() == null) {
            throw new ShopException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
        }
        String email = r.email().trim().toLowerCase();
        Outcome outcome = db.transaction(c -> {
            Instant now = Instant.now();
            UserAccount user = users.findByEmail(c, email).orElse(null);
            if (user == null) {
                hasher.verify(r.password(), dummyHash);
                audit.security(c, null, email, "LOGIN_FAILED", "USER", null, "Unknown email");
                return new Outcome(null, ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
            }
            if (user.isLocked(now)) {
                audit.security(c, user.id(), email, "LOGIN_BLOCKED", "USER", String.valueOf(user.id()), "Account is locked");
                return new Outcome(null, ErrorCode.ACCOUNT_LOCKED,
                        "Too many failed attempts. Try again in " + config.lockMinutes() + " minutes");
            }
            if (!hasher.verify(r.password(), user.passwordHash())) {
                int attempts = user.failedAttempts() + 1;
                if (attempts >= config.maxFailedLogins()) {
                    users.recordFailedLogin(c, user.id(), 0, now.plus(config.lockMinutes(), ChronoUnit.MINUTES));
                    audit.security(c, user.id(), email, "ACCOUNT_LOCKED", "USER", String.valueOf(user.id()),
                            "Too many failed logins");
                } else {
                    users.recordFailedLogin(c, user.id(), attempts, null);
                    audit.security(c, user.id(), email, "LOGIN_FAILED", "USER", String.valueOf(user.id()), "Wrong password");
                }
                return new Outcome(null, ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
            }
            if (user.status() == UserStatus.SUSPENDED) {
                audit.security(c, user.id(), email, "LOGIN_SUSPENDED", "USER", String.valueOf(user.id()), "Suspended account");
                return new Outcome(null, ErrorCode.ACCOUNT_SUSPENDED, "This account has been suspended");
            }
            users.recordSuccessfulLogin(c, user.id());
            if (user.role().isAdmin()) {
                audit.security(c, user.id(), email, "ADMIN_LOGIN", "USER", String.valueOf(user.id()), null);
            }
            return new Outcome(user, null, null);
        });
        if (outcome.error() != null) throw new ShopException(outcome.error(), outcome.message());
        UserAccount u = outcome.user();
        Session s = sessions.create(u.id(), u.email(), u.role(), connectionId);
        return new LoginResult(s.token(), u.toDto());
    }

    public void logout(Session session) {
        sessions.invalidate(session.token());
    }

    public UserDto profile(Session session) {
        return db.read(c -> users.findById(c, session.userId()).orElseThrow(() -> ShopException.notFound("Account")).toDto());
    }

    public UserDto updateProfile(Session session, UpdateProfileRequest r) {
        if (r == null) throw ShopException.invalid("Profile details are required");
        String first = Validator.personName(r.firstName(), "First name");
        String last = Validator.personName(r.lastName(), "Last name");
        String phone = Validator.phone(r.phone());
        return db.transaction(c -> {
            users.updateProfile(c, session.userId(), first, last, phone);
            return users.findById(c, session.userId()).orElseThrow().toDto();
        });
    }

    public void changePassword(Session session, ChangePasswordRequest r) {
        if (r == null) throw ShopException.invalid("Password details are required");
        db.transaction(c -> {
            UserAccount user = users.findById(c, session.userId()).orElseThrow(() -> ShopException.notFound("Account"));
            if (!hasher.verify(r.currentPassword(), user.passwordHash())) {
                throw new ShopException(ErrorCode.INVALID_CREDENTIALS, "Current password is incorrect");
            }
            Validator.password(r.newPassword(), user.email());
            if (r.newPassword().equals(r.currentPassword())) {
                throw ShopException.invalid("New password must be different from the current password");
            }
            users.updatePassword(c, user.id(), hasher.hash(r.newPassword()));
            audit.security(c, user.id(), user.email(), "PASSWORD_CHANGED", "USER", String.valueOf(user.id()), null);
            return null;
        });
        sessions.invalidateUserExcept(session.userId(), session.token());
    }

    public ArrayList<AddressDto> listAddresses(Session session) {
        return db.read(c -> addresses.list(c, session.userId()));
    }

    public AddressDto addAddress(Session session, AddressDto a) {
        if (a == null) throw ShopException.invalid("Address is required");
        AddressDto clean = new AddressDto(null, Validator.text(a.recipient(), "Recipient", 100),
                Validator.text(a.line1(), "Address line 1", 120), Validator.optionalText(a.line2(), "Address line 2", 120),
                Validator.text(a.city(), "City", 80), Validator.text(a.province(), "Province", 80),
                Validator.postalCode(a.postalCode()),
                a.country() == null || a.country().isBlank() ? "South Africa" : Validator.text(a.country(), "Country", 60),
                Validator.phone(a.phone()));
        return db.transaction(c -> {
            if (addresses.list(c, session.userId()).size() >= 10) {
                throw ShopException.invalid("You can save at most 10 addresses");
            }
            long id = addresses.insert(c, session.userId(), clean);
            return addresses.findOwned(c, id, session.userId()).orElseThrow();
        });
    }

    public void deleteAddress(Session session, long addressId) {
        boolean deleted = db.transaction(c -> addresses.delete(c, addressId, session.userId()));
        if (!deleted) throw ShopException.notFound("Address");
    }
}
