package com.shopsphere.server.dao;

import com.shopsphere.common.dto.AuthDtos.AddressDto;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Optional;

public class AddressDao {

    private static final String COLUMNS = "id, recipient, line1, line2, city, province, postal_code, country, phone";

    public ArrayList<AddressDto> list(Connection c, long userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + COLUMNS + " FROM addresses WHERE user_id = ? ORDER BY id")) {
            ps.setLong(1, userId);
            ArrayList<AddressDto> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
            return out;
        }
    }

    /** Only finds the address if it belongs to the user. */
    public Optional<AddressDto> findOwned(Connection c, long addressId, long userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + COLUMNS + " FROM addresses WHERE id = ? AND user_id = ?")) {
            ps.setLong(1, addressId);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public long insert(Connection c, long userId, AddressDto a) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO addresses (user_id, recipient, line1, line2, city, province, postal_code, country, phone) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id")) {
            ps.setLong(1, userId);
            ps.setString(2, a.recipient());
            ps.setString(3, a.line1());
            ps.setString(4, a.line2());
            ps.setString(5, a.city());
            ps.setString(6, a.province());
            ps.setString(7, a.postalCode());
            ps.setString(8, a.country());
            ps.setString(9, a.phone());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    public boolean delete(Connection c, long addressId, long userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM addresses WHERE id = ? AND user_id = ?")) {
            ps.setLong(1, addressId);
            ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    private AddressDto map(ResultSet rs) throws SQLException {
        return new AddressDto(rs.getLong("id"), rs.getString("recipient"), rs.getString("line1"), rs.getString("line2"),
                rs.getString("city"), rs.getString("province"), rs.getString("postal_code"), rs.getString("country"),
                rs.getString("phone"));
    }
}
