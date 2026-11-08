package com.shopsphere.server.dao;

import com.shopsphere.common.dto.AdminDtos.AuditLogDto;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class AuditDao {

    public void insert(Connection c, Long actorId, String actorEmail, String action, String category, String entityType,
                       String entityId, String details) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO audit_logs (actor_id, actor_email, action, category, entity_type, entity_id, details) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            Jdbc.setLong(ps, 1, actorId);
            ps.setString(2, actorEmail);
            ps.setString(3, action);
            ps.setString(4, category);
            ps.setString(5, entityType);
            ps.setString(6, entityId);
            ps.setString(7, details);
            ps.executeUpdate();
        }
    }

    /** Newest first. Security entries are only included when includeSecurity is true (SUPER_ADMIN). */
    public ArrayList<AuditLogDto> list(Connection c, int limit, boolean includeSecurity) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, actor_email, action, category, entity_type, entity_id, details, created_at FROM audit_logs "
                + "WHERE (? OR category <> 'SECURITY') ORDER BY id DESC LIMIT ?")) {
            ps.setBoolean(1, includeSecurity);
            ps.setInt(2, limit);
            ArrayList<AuditLogDto> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new AuditLogDto(rs.getLong("id"), rs.getString("actor_email"), rs.getString("action"),
                            rs.getString("category"), rs.getString("entity_type"), rs.getString("entity_id"),
                            rs.getString("details"), Jdbc.instant(rs, "created_at")));
                }
            }
            return out;
        }
    }
}
