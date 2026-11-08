package com.shopsphere.server.dao;

import com.shopsphere.common.protocol.Role;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;

/** Small helpers shared by the DAO classes. */
final class Jdbc {

    private Jdbc() { }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }

    static void setLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) ps.setNull(index, Types.BIGINT);
        else ps.setLong(index, value);
    }

    static Long getLong(ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }

    /** Make a LIKE pattern that matches the text literally. % and _ typed by a user are escaped. */
    static String contains(String text) {
        String escaped = text.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    static int roleId(Role role) {
        return role.ordinal() + 1;
    }

    static Role role(int id) {
        Role[] all = Role.values();
        if (id < 1 || id > all.length) throw new IllegalStateException("Unknown role id " + id);
        return all[id - 1];
    }
}
