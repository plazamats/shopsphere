package com.shopsphere.server.dao;

import com.shopsphere.common.dto.AdminDtos.SettingDto;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SettingsDao {

    public Map<String, String> asMap(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT setting_key, setting_value FROM system_settings");
             ResultSet rs = ps.executeQuery()) {
            Map<String, String> map = new HashMap<>();
            while (rs.next()) map.put(rs.getString(1), rs.getString(2));
            return map;
        }
    }

    public ArrayList<SettingDto> list(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT setting_key, setting_value, description FROM system_settings ORDER BY setting_key");
             ResultSet rs = ps.executeQuery()) {
            ArrayList<SettingDto> out = new ArrayList<>();
            while (rs.next()) out.add(new SettingDto(rs.getString(1), rs.getString(2), rs.getString(3)));
            return out;
        }
    }

    public boolean update(Connection c, String key, String value, long updatedBy) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE system_settings SET setting_value = ?, updated_by = ?, updated_at = now() WHERE setting_key = ?")) {
            ps.setString(1, value);
            ps.setLong(2, updatedBy);
            ps.setString(3, key);
            return ps.executeUpdate() > 0;
        }
    }
}
