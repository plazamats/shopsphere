package com.shopsphere.server.network;

import com.shopsphere.common.protocol.ErrorCode;
import com.shopsphere.common.protocol.Response;
import com.shopsphere.server.security.SessionManager;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/** Accepts TCP connections and gives each one to a thread from a bounded pool. */
public class ShopServer {

    private static final Logger LOG = Logger.getLogger(ShopServer.class.getName());
    private static final int IDLE_TIMEOUT_MS = 30 * 60 * 1000;

    private final int port;
    private final RequestRouter router;
    private final SessionManager sessions;
    private final ExecutorService pool;
    private final AtomicLong ids = new AtomicLong();
    private volatile ServerSocket serverSocket;
    private volatile boolean running;

    public ShopServer(int port, int maxClients, RequestRouter router, SessionManager sessions) {
        this.port = port;
        this.router = router;
        this.sessions = sessions;
        // No queue: when every thread is busy a new client is told the server is busy.
        this.pool = new ThreadPoolExecutor(maxClients, maxClients, 60, TimeUnit.SECONDS, new SynchronousQueue<>(),
                r -> {
                    Thread t = new Thread(r, "client-handler");
                    t.setDaemon(true);
                    return t;
                });
    }

    /** Bind the port (use 0 for any free port) and start accepting on a background thread. */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        Thread acceptor = new Thread(this::acceptLoop, "acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
        LOG.info("ShopSphere server listening on port " + serverSocket.getLocalPort());
    }

    public int port() {
        return serverSocket.getLocalPort();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(IDLE_TIMEOUT_MS);
                socket.setTcpNoDelay(true);
                long id = ids.incrementAndGet();
                try {
                    pool.execute(new ClientHandler(socket, id, router, sessions));
                } catch (RejectedExecutionException e) {
                    refuse(socket);
                }
            } catch (IOException e) {
                if (running) LOG.warning("Accept failed: " + e.getMessage());
            }
        }
    }

    private void refuse(Socket socket) {
        try (socket) {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.writeObject(Response.failure(null, ErrorCode.SERVER_BUSY, "The server is busy. Please try again shortly"));
            out.flush();
        } catch (IOException e) {
            LOG.fine("Could not tell a client the server is busy");
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {
            LOG.fine("Closing the server socket: " + e.getMessage());
        }
        pool.shutdownNow();
        LOG.info("Server stopped");
    }
}
