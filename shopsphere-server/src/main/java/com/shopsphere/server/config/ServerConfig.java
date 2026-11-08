package com.shopsphere.server.config;

import com.shopsphere.common.protocol.ProtocolSupport;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record ServerConfig(String dbUrl, String dbUser, String dbPassword, int port, int maxClients,
                           int sessionIdleMinutes, int pbkdf2Iterations, int maxFailedLogins, int lockMinutes,
                           String superAdminEmail, String superAdminPassword) {

    public static final String DEFAULT_DB_URL = "jdbc:postgresql://localhost:5432/shopsphere";

   
    public static ServerConfig load() {
        Properties file = new Properties();
        Path path = Path.of(System.getenv().getOrDefault("SHOPSPHERE_CONFIG", "config/server.local.properties"));
        if (Files.isReadable(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                file.load(in);
            } catch (IOException e) {
                throw new IllegalStateException("Could not read " + path + ": " + e.getMessage(), e);
            }
        }
        return new ServerConfig(
                get(file, "SHOPSPHERE_DB_URL", "db.url", DEFAULT_DB_URL),
                get(file, "SHOPSPHERE_DB_USER", "db.user", "shopsphere"),
                get(file, "SHOPSPHERE_DB_PASSWORD", "db.password", ""),
                Integer.parseInt(get(file, "SHOPSPHERE_PORT", "server.port", String.valueOf(ProtocolSupport.DEFAULT_PORT))),
                Integer.parseInt(get(file, "SHOPSPHERE_MAX_CLIENTS", "server.maxClients", "50")),
                Integer.parseInt(get(file, "SHOPSPHERE_SESSION_MINUTES", "session.idleMinutes", "30")),
                Integer.parseInt(get(file, "SHOPSPHERE_PBKDF2_ITERATIONS", "security.pbkdf2Iterations", "210000")),
                Integer.parseInt(get(file, "SHOPSPHERE_MAX_FAILED_LOGINS", "security.maxFailedLogins", "5")),
                Integer.parseInt(get(file, "SHOPSPHERE_LOCK_MINUTES", "security.lockMinutes", "15")),
                get(file, "SHOPSPHERE_SUPERADMIN_EMAIL", "bootstrap.superAdminEmail", ""),
                get(file, "SHOPSPHERE_SUPERADMIN_PASSWORD", "bootstrap.superAdminPassword", ""));
    }

    public static ServerConfig forTests(String dbUrl, String dbUser, String dbPassword) {
        return new ServerConfig(dbUrl, dbUser, dbPassword, 0, 50, 30, 1000, 5, 15, "", "");
    }

    public ServerConfig withBootstrap(String email, String password) {
        return new ServerConfig(dbUrl, dbUser, dbPassword, port, maxClients, sessionIdleMinutes, pbkdf2Iterations,
                maxFailedLogins, lockMinutes, email, password);
    }

    private static String get(Properties file, String envName, String fileKey, String defaultValue) {
        String fromEnv = System.getenv(envName);
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv.trim();
        return file.getProperty(fileKey, defaultValue).trim();
    }


    @Override
    public String toString() {
        return "ServerConfig[dbUrl=" + dbUrl + ", dbUser=" + dbUser + ", port=" + port
                + ", maxClients=" + maxClients + "]";
    }
}
