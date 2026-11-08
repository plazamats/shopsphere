package com.shopsphere.server.service;

import com.shopsphere.common.dto.AuthDtos.CreateAdminRequest;
import com.shopsphere.common.dto.AuthDtos.RoleChangeRequest;
import com.shopsphere.common.dto.AuthDtos.UserDto;
import com.shopsphere.common.dto.AuthDtos.UserStatusRequest;
import com.shopsphere.common.protocol.ErrorCode;
import com.shopsphere.common.protocol.Role;
import com.shopsphere.common.protocol.UserStatus;
import com.shopsphere.server.dao.UserDao;
import com.shopsphere.server.database.Database;
import com.shopsphere.server.model.UserAccount;
import com.shopsphere.server.security.PasswordHasher;
import com.shopsphere.server.security.Session;
import com.shopsphere.server.security.SessionManager;
import com.shopsphere.server.validation.Validator;
import java.util.ArrayList;
import java.util.List;

/**
 * Managing other people's accounts. Rules enforced here (not in the client):
 * an ADMIN may only suspend customers; a SUPER_ADMIN may also manage admins; nobody changes their own
 * status or role; nobody can be given SUPER_ADMIN through the protocol.
 */
public class UserAdminService {

    private final Database db;
    private final UserDao users;
    private final PasswordHasher hasher;
    private final SessionManager sessions;
    private final AuditService audit;

    public UserAdminService(Database db, UserDao users, PasswordHasher hasher, SessionManager sessions, AuditService audit) {
        this.db = db;
        this.users = users;
        this.hasher = hasher;
        this.sessions = sessions;
        this.audit = audit;
    }

    public ArrayList<UserDto> listCustomers(String search) {
        String s = Validator.search(search);
        return toDtos(db.read(c -> users.list(c, s, List.of(Role.CUSTOMER))));
    }

    public ArrayList<UserDto> listAdmins(Session actor) {
        requireSuperAdmin(actor);
        return toDtos(db.read(c -> users.list(c, null, List.of(Role.ADMIN, Role.SUPER_ADMIN))));
    }

    public UserDto setStatus(Session actor, UserStatusRequest r) {
        if (r == null || r.status() == null) throw ShopException.invalid("User and status are required");
        // Decide inside a transaction. A refusal is written to the audit log in its own transaction.
        UserDto result;
        try {
            result = db.transaction(c -> {
                UserAccount target = users.findByIdForUpdate(c, r.userId()).orElseThrow(() -> ShopException.notFound("User"));
                if (target.id() == actor.userId()) {
                    throw ShopException.forbidden("You cannot change the status of your own account");
                }
                if (!mayManage(actor.role(), target.role())) {
                    throw ShopException.forbidden("You do not have permission to manage this account");
                }
                users.setStatus(c, target.id(), r.status());
                audit.admin(c, actor, r.status() == UserStatus.SUSPENDED ? "USER_SUSPENDED" : "USER_REACTIVATED",
                        "USER", String.valueOf(target.id()), target.email());
                return users.findById(c, target.id()).orElseThrow().toDto();
            });
        } catch (ShopException e) {
            if (e.code() == ErrorCode.FORBIDDEN) {
                audit.securityStandalone(actor.userId(), actor.email(), "STATUS_CHANGE_REFUSED", "USER",
                        String.valueOf(r.userId()), e.getMessage());
            }
            throw e;
        }
        if (r.status() == UserStatus.SUSPENDED) sessions.invalidateUser(r.userId());
        return result;
    }

    public UserDto createAdmin(Session actor, CreateAdminRequest r) {
        requireSuperAdmin(actor);
        if (r == null) throw ShopException.invalid("Administrator details are required");
        String email = Validator.email(r.email());
        Validator.password(r.password(), email);
        String first = Validator.personName(r.firstName(), "First name");
        String last = Validator.personName(r.lastName(), "Last name");
        String hash = hasher.hash(r.password());
        try {
            return db.transaction(c -> {
                if (users.findByEmail(c, email).isPresent()) {
                    throw new ShopException(ErrorCode.CONFLICT, "An account with this email already exists");
                }
                long id = users.insert(c, email, first, last, null, hash, Role.ADMIN);
                audit.security(c, actor.userId(), actor.email(), "ADMIN_CREATED", "USER", String.valueOf(id), email);
                return users.findById(c, id).orElseThrow().toDto();
            });
        } catch (RuntimeException e) {
            if (Database.isUniqueViolation(e)) throw new ShopException(ErrorCode.CONFLICT, "An account with this email already exists");
            throw e;
        }
    }

    /** SUPER_ADMIN may change a user between CUSTOMER and ADMIN. Granting SUPER_ADMIN is never allowed. */
    public UserDto setRole(Session actor, RoleChangeRequest r) {
        requireSuperAdmin(actor);
        if (r == null || r.role() == null) throw ShopException.invalid("User and role are required");
        if (r.role() == Role.SUPER_ADMIN) {
            audit.securityStandalone(actor.userId(), actor.email(), "PRIVILEGE_ESCALATION_BLOCKED", "USER",
                    String.valueOf(r.userId()), "Attempt to grant SUPER_ADMIN");
            throw ShopException.forbidden("The SUPER_ADMIN role cannot be assigned");
        }
        UserDto result;
        try {
            result = db.transaction(c -> {
                UserAccount target = users.findByIdForUpdate(c, r.userId()).orElseThrow(() -> ShopException.notFound("User"));
                if (target.id() == actor.userId()) throw ShopException.forbidden("You cannot change your own role");
                if (target.role() == Role.SUPER_ADMIN) throw ShopException.forbidden("A SUPER_ADMIN account cannot be changed");
                users.setRole(c, target.id(), r.role());
                audit.security(c, actor.userId(), actor.email(), "ROLE_CHANGED", "USER", String.valueOf(target.id()),
                        target.email() + ": " + target.role() + " to " + r.role());
                return users.findById(c, target.id()).orElseThrow().toDto();
            });
        } catch (ShopException e) {
            if (e.code() == ErrorCode.FORBIDDEN) {
                audit.securityStandalone(actor.userId(), actor.email(), "ROLE_CHANGE_REFUSED", "USER",
                        String.valueOf(r.userId()), e.getMessage());
            }
            throw e;
        }
        sessions.invalidateUser(r.userId());   // the user must log in again with the new role
        return result;
    }

    private static boolean mayManage(Role actor, Role target) {
        if (actor == Role.SUPER_ADMIN) return target != Role.SUPER_ADMIN;
        if (actor == Role.ADMIN) return target == Role.CUSTOMER;
        return false;
    }

    private static void requireSuperAdmin(Session actor) {
        if (actor == null || actor.role() != Role.SUPER_ADMIN) {
            throw ShopException.forbidden("Only a SUPER_ADMIN can do this");
        }
    }

    private static ArrayList<UserDto> toDtos(List<UserAccount> list) {
        ArrayList<UserDto> out = new ArrayList<>();
        for (UserAccount u : list) out.add(u.toDto());
        return out;
    }
}
