package com.shopsphere.client.network;

import com.shopsphere.common.protocol.ErrorCode;
import com.shopsphere.common.protocol.ProtocolSupport;
import com.shopsphere.common.protocol.Request;
import com.shopsphere.common.protocol.Response;
import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;


public class ServerConnection implements Closeable {

    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int READ_TIMEOUT_MS = 30000;

    private final Socket socket = new Socket();
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public void connect(String host, int port) throws ApiException {
        try {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = ProtocolSupport.filteredInput(socket.getInputStream());
        } catch (IOException e) {
            close();
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Cannot reach the ShopSphere server at " + host + ":" + port + ". Is it running?");
        }
    }

    public boolean isConnected() {
        return socket.isConnected() && !socket.isClosed();
    }

    public synchronized Response send(Request request) throws ApiException {
        try {
            out.writeObject(request);
            out.flush();
            out.reset();
            Object answer = in.readObject();
            if (answer instanceof Response r) return r;
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "The server sent an unexpected answer");
        } catch (IOException | ClassNotFoundException e) {
            close();
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "The connection to the server was lost. Please restart the app");
        }
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // nothing useful to do when closing fails
            return;
        }
    }
}
