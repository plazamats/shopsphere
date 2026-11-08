package com.shopsphere.server.service;

import com.shopsphere.common.protocol.Role;
import com.shopsphere.server.config.ServerConfig;
import com.shopsphere.server.dao.UserDao;
import com.shopsphere.server.database.Database;
import com.shopsphere.server.security.PasswordHasher;
import com.shopsphere.server.validation.Validator;
import java.util.logging.Logger;

/**
 * Creates the first SUPER_ADMIN when none exists. The email and password come from the environment,
 * so no credential is ever written in source code or in the seed data.
 */
public class BootstrapService {

    private static final Logger LOG = Logger.getLogger(BootstrapService.class.getName());

    private final Database db;
    private final UserDao users;
    private final PasswordHasher hasher;

    public BootstrapService(Database db, UserDao users, PasswordHasher hasher) {
        this.db = db;
        this.users = users;
        this.hasher = hasher;
    }

    public void ensureSuperAdmin(ServerConfig config) {
        boolean exists = db.read(c -> users.existsWithRole(c, Role.SUPER_ADMIN));
        if (exists) return;
        if (config.superAdminEmail() == null || config.superAdminEmail().isBlank()
                || config.superAdminPassword() == null || config.superAdminPassword().isBlank()) {
            LOG.warning("No SUPER_ADMIN exists. Set SHOPSPHERE_SUPERADMIN_EMAIL and SHOPSPHERE_SUPERADMIN_PASSWORD to create one.");
            return;
        }
        String email = Validator.email(config.superAdminEmail());
        String password = Validator.password(config.superAdminPassword(), email);
        db.transaction(c -> {
            users.insert(c, email, "System", "Owner", null, hasher.hash(password), Role.SUPER_ADMIN);
            return null;
        });
        LOG.info("Created the SUPER_ADMIN account " + email);
    }
}
