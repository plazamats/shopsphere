package com.shopsphere.server.support;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.shopsphere.common.protocol.Command;
import com.shopsphere.common.protocol.ErrorCode;
import com.shopsphere.common.protocol.Request;
import com.shopsphere.common.protocol.Response;
import com.shopsphere.server.ServerContext;
import com.shopsphere.server.config.ServerConfig;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Assumptions;

/**
 * Database tests run only against a database whose name ends in _test, because every test class
 * drops and recreates the schema. Set SHOPSPHERE_TEST_DB_URL, SHOPSPHERE_TEST_DB_USER and
 * SHOPSPHERE_TEST_DB_PASSWORD. Without them the database tests are skipped.
 */
public final class TestEnv {

    public static final String OWNER_EMAIL = "owner@shopsphere.test";
    public static final String OWNER_PASSWORD = "Owner#Secret1";
    public static final String CUSTOMER_EMAIL = "customer@shopsphere.test";
    public static final String CUSTOMER_PASSWORD = "Customer#123";
    public static final String ADMIN_EMAIL = "admin@shopsphere.test";
    public static final String ADMIN_PASSWORD = "Admin#12345";
    public static final String GOOD_CARD = "4242 4242 4242 4242";
    public static final String DECLINED_CARD = "4000 0000 0000 0002";

    private static final AtomicLong CONNECTIONS = new AtomicLong(1000);

    private final ServerContext context;
    private final ServerConfig config;

    private TestEnv(ServerConfig config, ServerContext context) {
        this.config = config;
        this.context = context;
    }

    /** Skips the test class when no safe test database is configured. */
    public static TestEnv fresh() {
        String url = System.getenv("SHOPSPHERE_TEST_DB_URL");
        String user = System.getenv("SHOPSPHERE_TEST_DB_USER");
        String password = System.getenv("SHOPSPHERE_TEST_DB_PASSWORD");
        Assumptions.assumeTrue(url != null && user != null && password != null, "no test database configured");
        Assumptions.assumeTrue(url.endsWith("_test"), "the test database name must end in _test");
        try (Connection c = DriverManager.getConnection(url, user, password); Statement st = c.createStatement()) {
            st.execute("DROP SCHEMA public CASCADE");
            st.execute("CREATE SCHEMA public");
            st.execute(read("database/schema.sql"));
            st.execute(read("database/seed.sql"));
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "test database not reachable: " + e.getMessage());
        }
        ServerConfig config = ServerConfig.forTests(url, user, password).withBootstrap(OWNER_EMAIL, OWNER_PASSWORD);
        ServerContext ctx = ServerContext.create(config);
        ctx.bootstrap().ensureSuperAdmin(config);
        return new TestEnv(config, ctx);
    }

    private static String read(String relative) throws Exception {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve(relative))) dir = dir.getParent();
        assertTrue(dir != null, "cannot find " + relative);
        return Files.readString(dir.resolve(relative), StandardCharsets.UTF_8);
    }

    public ServerContext context() { return context; }
    public ServerConfig config() { return config; }

    public TestClient client() {
        return new TestClient(this, CONNECTIONS.incrementAndGet());
    }

    /** A fake connection: calls the router directly, the same code path the socket handler uses. */
    public static final class TestClient {
        private final TestEnv env;
        private final long connectionId;
        private String token;

        TestClient(TestEnv env, long connectionId) {
            this.env = env;
            this.connectionId = connectionId;
        }

        public long connectionId() { return connectionId; }
        public String token() { return token; }
        public void setToken(String token) { this.token = token; }

        public Response send(Command command, Serializable payload) {
            return env.context.router().handle(new Request(UUID.randomUUID().toString(), command, token, payload), connectionId);
        }

        @SuppressWarnings("unchecked")
        public <T> T ok(Command command, Serializable payload) {
            Response r = send(command, payload);
            if (!r.ok()) fail(command + " failed: " + r.code() + " " + r.message());
            return (T) r.data();
        }

        public ErrorCode fails(Command command, Serializable payload) {
            Response r = send(command, payload);
            if (r.ok()) fail(command + " was expected to fail but succeeded");
            return r.code();
        }

        public com.shopsphere.common.dto.AuthDtos.UserDto login(String email, String password) {
            com.shopsphere.common.dto.AuthDtos.LoginResult r = ok(Command.LOGIN,
                    new com.shopsphere.common.dto.AuthDtos.LoginRequest(email, password));
            token = r.token();
            return r.user();
        }
    }
}
