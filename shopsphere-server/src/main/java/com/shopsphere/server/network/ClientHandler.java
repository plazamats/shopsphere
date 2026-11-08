package com.shopsphere.server.network;

import com.shopsphere.common.protocol.ErrorCode;
import com.shopsphere.common.protocol.ProtocolSupport;
import com.shopsphere.common.protocol.Request;
import com.shopsphere.common.protocol.Response;
import com.shopsphere.server.security.SessionManager;
import java.io.EOFException;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Serves one connected client: read a request, answer it, repeat. Cleans up its sessions on disconnect. */
class ClientHandler implements Runnable {

    private static final Logger LOG = Logger.getLogger(ClientHandler.class.getName());

    private final Socket socket;
    private final long connectionId;
    private final RequestRouter router;
    private final SessionManager sessions;

    ClientHandler(Socket socket, long connectionId, RequestRouter router, SessionManager sessions) {
        this.socket = socket;
        this.connectionId = connectionId;
        this.router = router;
        this.sessions = sessions;
    }

    @Override
    public void run() {
        LOG.info("Client " + connectionId + " connected");
        try (socket) {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = ProtocolSupport.filteredInput(socket.getInputStream());
            while (!socket.isClosed()) {
                Object received = in.readObject();
                Response response;
                if (received instanceof Request req) {
                    response = router.handle(req, connectionId);
                } else {
                    response = Response.failure(null, ErrorCode.BAD_REQUEST, "Invalid request");
                }
                out.writeObject(response);
                out.flush();
                out.reset();
            }
        } catch (EOFException | SocketTimeoutException e) {
            LOG.fine("Client " + connectionId + " ended: " + e.getClass().getSimpleName());
        } catch (InvalidClassException e) {
            LOG.warning("Client " + connectionId + " sent a class that is not allowed");
        } catch (IOException | ClassNotFoundException e) {
            LOG.log(Level.FINE, "Client " + connectionId + " connection error: " + e.getMessage());
        } finally {
            sessions.invalidateConnection(connectionId);
            LOG.info("Client " + connectionId + " disconnected");
        }
    }
}
