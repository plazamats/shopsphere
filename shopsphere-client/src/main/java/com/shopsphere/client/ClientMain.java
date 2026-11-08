package com.shopsphere.client;

import com.shopsphere.client.network.ApiClient;
import com.shopsphere.client.network.ApiException;
import com.shopsphere.client.network.ServerConnection;
import com.shopsphere.client.ui.LoginFrame;
import com.shopsphere.client.ui.Theme;
import com.shopsphere.common.protocol.ProtocolSupport;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public final class ClientMain {

    private ClientMain() { }

    public static void main(String[] args) {
        String host = env("SHOPSPHERE_HOST", "localhost");
        int port = Integer.parseInt(env("SHOPSPHERE_PORT", String.valueOf(ProtocolSupport.DEFAULT_PORT)));
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            ServerConnection connection = new ServerConnection();
            try {
                connection.connect(host, port);
            } catch (ApiException e) {
                JOptionPane.showMessageDialog(null, e.getMessage(), "ShopSphere", JOptionPane.ERROR_MESSAGE);
                return;
            }
            new LoginFrame(new ApiClient(connection), connection).setVisible(true);
        });
    }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? fallback : v;
    }
}
