package com.shopsphere.server.dao;

import com.shopsphere.common.protocol.Role;
import com.shopsphere.common.protocol.UserStatus;
import com.shopsphere.server.model.UserAccount;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserDao {

    private static final String SELECT =
            "SELECT u.id, u.email, u.first_name, u.last_name, u.phone, u.password_hash, u.status, "
            + "u.failed_attempts, u.locked_until, u.created_at, u.last_login_at, "
            + "(SELECT max(ur.role_id) FROM user_roles ur WHERE ur.user_id = u.id) AS role_id FROM users u ";

    public Optional<UserAccount> findByEmail(Connection c, String email) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + "WHERE lower(u.email) = lower(?)")) {
            ps.setString(1, email);
            return one(ps);
        }
    }

    public Optional<UserAccount> findById(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + "WHERE u.id = ?")) {
            ps.setLong(1, id);
            return one(ps);
        }
    }

    /** Lock the user row so two administrators cannot change the same account at the same time. */
    public Optional<UserAccount> findByIdForUpdate(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + "WHERE u.id = ? FOR UPDATE OF u")) {
            ps.setLong(1, id);
            return one(ps);
        }
    }

    public long insert(Connection c, String email, String firstName, String lastName, String phone,
                       String passwordHash, Role role) throws SQLException {
        long id;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO users (email, first_name, last_name, phone, password_hash) VALUES (?, ?, ?, ?, ?) RETURNING id")) {
            ps.setString(1, email);
            ps.setString(2, firstName);
            ps.setString(3, lastName);
            ps.setString(4, phone);
            ps.setString(5, passwordHash);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                id = rs.getLong(1);
            }
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)")) {
            ps.setLong(1, id);
            ps.setInt(2, Jdbc.roleId(role));
            ps.executeUpdate();
        }
        return id;
    }

    public void updateProfile(Connection c, long id, String firstName, String lastName, String phone) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE users SET first_name = ?, last_name = ?, phone = ?, updated_at = now() WHERE id = ?")) {
            ps.setString(1, firstName);
            ps.setString(2, lastName);
            ps.setString(3, phone);
            ps.setLong(4, id);
            ps.executeUpdate();
        }
    }

    public void updatePassword(Connection c, long id, String passwordHash) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE users SET password_hash = ?, updated_at = now() WHERE id = ?")) {
            ps.setString(1, passwordHash);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public void setStatus(Connection c, long id, UserStatus status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE users SET status = ?, updated_at = now() WHERE id = ?")) {
            ps.setString(1, status.name());
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public void recordFailedLogin(Connection c, long id, int attempts, Instant lockedUntil) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE users SET failed_attempts = ?, locked_until = ? WHERE id = ?")) {
            ps.setInt(1, attempts);
            ps.setTimestamp(2, lockedUntil == null ? null : Timestamp.from(lockedUntil));
            ps.setLong(3, id);
            ps.executeUpdate();
        }
    }

    public void recordSuccessfulLogin(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE users SET failed_attempts = 0, locked_until = NULL, last_login_at = now() WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    public void setRole(Connection c, long id, Role role) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM user_roles WHERE user_id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)")) {
            ps.setLong(1, id);
            ps.setInt(2, Jdbc.roleId(role));
            ps.executeUpdate();
        }
    }

    /** Users whose role is one of the given roles. The search matches email and name. */
    public List<UserAccount> list(Connection c, String search, List<Role> roles) throws SQLException {
        Integer[] roleIds = roles.stream().map(Jdbc::roleId).toArray(Integer[]::new);
        String sql = SELECT + "WHERE (SELECT max(ur.role_id) FROM user_roles ur WHERE ur.user_id = u.id) = ANY (?) "
                + "AND (CAST(? AS VARCHAR) IS NULL OR lower(u.email) LIKE ? ESCAPE '\\' "
                + "OR lower(u.first_name || ' ' || u.last_name) LIKE ? ESCAPE '\\') ORDER BY u.created_at, u.id";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setArray(1, c.createArrayOf("integer", roleIds));
            String pattern = search == null ? null : Jdbc.contains(search);
            ps.setString(2, search);
            ps.setString(3, pattern);
            ps.setString(4, pattern);
            List<UserAccount> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
            return out;
        }
    }

    public boolean existsWithRole(Connection c, Role role) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM user_roles WHERE role_id = ? LIMIT 1")) {
            ps.setInt(1, Jdbc.roleId(role));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private Optional<UserAccount> one(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.of(map(rs)) : Optional.empty();
        }
    }

    private UserAccount map(ResultSet rs) throws SQLException {
        return new UserAccount(rs.getLong("id"), rs.getString("email"), rs.getString("first_name"),
                rs.getString("last_name"), rs.getString("phone"), rs.getString("password_hash"),
                UserStatus.valueOf(rs.getString("status")), rs.getInt("failed_attempts"),
                Jdbc.instant(rs, "locked_until"), Jdbc.role(rs.getInt("role_id")),
                Jdbc.instant(rs, "created_at"), Jdbc.instant(rs, "last_login_at"));
    }
}
