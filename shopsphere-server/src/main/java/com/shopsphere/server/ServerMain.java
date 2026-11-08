package com.shopsphere.server;

import com.shopsphere.server.config.LoggingConfig;
import com.shopsphere.server.config.ServerConfig;
import com.shopsphere.server.network.ShopServer;
import java.util.logging.Level;
import java.util.logging.Logger;


public final class ServerMain {

    private static final Logger LOG = Logger.getLogger(ServerMain.class.getName());

    private ServerMain() { }

    public static void main(String[] args) throws Exception {
        LoggingConfig.configure(Level.INFO);
        ServerConfig config = ServerConfig.load();
        ServerContext ctx = ServerContext.create(config);
        if (!ctx.database().ping()) {
            LOG.severe("Cannot reach the database. Check SHOPSPHERE_DB_URL, SHOPSPHERE_DB_USER and SHOPSPHERE_DB_PASSWORD");
            System.exit(1);
        }
        ctx.bootstrap().ensureSuperAdmin(config);
        ShopServer server = ctx.newServer();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
        Thread.currentThread().join();
    }
}
