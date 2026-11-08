package com.shopsphere.server.service;

import com.shopsphere.server.dao.AuditDao;
import com.shopsphere.server.database.Database;
import com.shopsphere.server.security.Session;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

/** Writes the audit trail. ADMIN entries are normal admin actions, SECURITY entries are for SUPER_ADMIN. */
public class AuditService {

    private static final Logger LOG = Logger.getLogger(AuditService.class.getName());

    private final AuditDao dao;
    private final Database db;

    public AuditService(Database db, AuditDao dao) {
        this.db = db;
        this.dao = dao;
    }

    /** Write inside the caller's transaction, so the action and its audit entry commit together. */
    public void admin(Connection c, Session actor, String action, String entityType, String entityId, String details)
            throws SQLException {
        write(c, actor.userId(), actor.email(), action, "ADMIN", entityType, entityId, details);
    }

    public void security(Connection c, Long actorId, String actorEmail, String action, String entityType,
                         String entityId, String details) throws SQLException {
        write(c, actorId, actorEmail, action, "SECURITY", entityType, entityId, details);
    }

    /** Write in its own transaction. Used when the main action is refused and rolled back. */
    public void securityStandalone(Long actorId, String actorEmail, String action, String entityType,
                                   String entityId, String details) {
        db.transaction(c -> {
            security(c, actorId, actorEmail, action, entityType, entityId, details);
            return null;
        });
    }

    private void write(Connection c, Long actorId, String actorEmail, String action, String category,
                       String entityType, String entityId, String details) throws SQLException {
        String safe = details == null ? null : (details.length() > 480 ? details.substring(0, 480) : details);
        dao.insert(c, actorId, actorEmail, action, category, entityType, entityId, safe);
        LOG.info("AUDIT " + category + " " + action + " by " + actorEmail + " " + entityType + ":" + entityId);
    }
}
