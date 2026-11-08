package com.shopsphere.server.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shopsphere.common.dto.AuthDtos.LoginRequest;
import com.shopsphere.common.dto.AuthDtos.LoginResult;
import com.shopsphere.common.dto.CatalogDtos.ProductDto;
import com.shopsphere.common.dto.ShoppingDtos.CartDto;
import com.shopsphere.common.dto.ShoppingDtos.CartUpdate;
import com.shopsphere.common.protocol.Command;
import com.shopsphere.common.protocol.ErrorCode;
import com.shopsphere.common.protocol.ProtocolSupport;
import com.shopsphere.common.protocol.Request;
import com.shopsphere.common.protocol.Response;
import com.shopsphere.server.network.ShopServer;
import com.shopsphere.server.support.Helpers;
import com.shopsphere.server.support.TestEnv;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Real TCP sockets, real protocol, several clients at once. */
class SocketIntegrationTest {

    private Helpers h;
    private ShopServer server;

    private static final class Conn implements AutoCloseable {
        final Socket socket;
        final ObjectOutputStream out;
        final ObjectInputStream in;
        String token;

        Conn(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(15000);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = ProtocolSupport.filteredInput(socket.getInputStream());
        }

        Response send(Command c, Serializable payload) throws Exception {
            out.writeObject(new Request(UUID.randomUUID().toString(), c, token, payload));
            out.flush();
            out.reset();
            return (Response) in.readObject();
        }

        @Override public void close() throws IOException { socket.close(); }
    }

    @BeforeEach
    void setUp() throws Exception {
        h = Helpers.fresh();
        server = h.env.context().newServer();
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
    }

    @Test
    void manyClientsWorkAtTheSameTimeWithoutMixingUpTheirData() throws Exception {
        int clients = 8;
        for (int i = 0; i < clients; i++) h.newCustomer("sock" + i + "@example.com");
        ExecutorService pool = Executors.newFixedThreadPool(clients);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < clients; i++) {
            final int n = i;
            results.add(pool.submit(() -> {
                try (Conn c = new Conn(server.port())) {
                    Response login = c.send(Command.LOGIN, new LoginRequest("sock" + n + "@example.com", "Passw0rd!x"));
                    assertTrue(login.ok());
                    c.token = ((LoginResult) login.data()).token();
                    Response products = c.send(Command.LIST_PRODUCTS, null);
                    @SuppressWarnings("unchecked") ArrayList<ProductDto> list = (ArrayList<ProductDto>) products.data();
                    long productId = list.get(n).id();
                    for (int k = 0; k < 20; k++) c.send(Command.GET_PROFILE, null);
                    Response cart = c.send(Command.ADD_TO_CART, new CartUpdate(productId, n + 1));
                    assertTrue(cart.ok(), cart.message());
                    CartDto dto = (CartDto) c.send(Command.GET_CART, null).data();
                    assertEquals(1, dto.items().size());
                    return dto.items().get(0).quantity();
                }
            }));
        }
        for (int i = 0; i < clients; i++) assertEquals(i + 1, results.get(i).get(60, TimeUnit.SECONDS).intValue());
        pool.shutdownNow();
    }

    @Test
    void aTokenFromOneConnectionDoesNotWorkOnAnother() throws Exception {
        try (Conn a = new Conn(server.port()); Conn b = new Conn(server.port())) {
            Response login = a.send(Command.LOGIN, new LoginRequest(TestEnv.CUSTOMER_EMAIL, TestEnv.CUSTOMER_PASSWORD));
            a.token = ((LoginResult) login.data()).token();
            assertTrue(a.send(Command.GET_PROFILE, null).ok());
            b.token = a.token;
            Response stolen = b.send(Command.GET_PROFILE, null);
            assertFalse(stolen.ok());
            assertEquals(ErrorCode.UNAUTHENTICATED, stolen.code());
        }
    }

    @Test
    void disconnectingEndsTheSession() throws Exception {
        String token;
        try (Conn a = new Conn(server.port())) {
            Response login = a.send(Command.LOGIN, new LoginRequest(TestEnv.CUSTOMER_EMAIL, TestEnv.CUSTOMER_PASSWORD));
            token = ((LoginResult) login.data()).token();
        }
        Thread.sleep(500);
        assertEquals(0, h.env.context().sessions().activeCount());
        assertFalse(token.isEmpty());
    }

    @Test
    void aClassThatIsNotOnTheAllowListIsRejectedAndTheServerKeepsRunning() throws Exception {
        try (Conn bad = new Conn(server.port())) {
            boolean closed = false;
            try {
                bad.out.writeObject(new File("/etc/passwd"));
                bad.out.flush();
                bad.in.readObject();
            } catch (IOException e) {
                closed = true;
            }
            assertTrue(closed, "the server must drop a client that sends a forbidden class");
        }
        try (Conn good = new Conn(server.port())) {
            assertTrue(good.send(Command.LIST_PRODUCTS, null).ok());
        }
    }

    @Test
    void aMalformedRequestGetsASafeError() throws Exception {
        try (Conn c = new Conn(server.port())) {
            Response r = c.send(Command.LIST_PRODUCTS, "this is not a ProductQuery");
            assertFalse(r.ok());
            assertEquals(ErrorCode.BAD_REQUEST, r.code());
            assertTrue(c.send(Command.LIST_PRODUCTS, null).ok(), "the connection must survive a bad request");
        }
    }

    @Test
    void theServerDoesNotWriteStackTracesToClients() throws Exception {
        try (Conn c = new Conn(server.port())) {
            Response r = c.send(Command.GET_PRODUCT, "not a long");
            assertFalse(r.ok());
            assertFalse(String.valueOf(r.message()).contains("Exception"));
            assertFalse(String.valueOf(r.message()).contains("com.shopsphere"));
        }
    }
}
