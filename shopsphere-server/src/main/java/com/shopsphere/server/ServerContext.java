package com.shopsphere.server;

import com.shopsphere.server.config.ServerConfig;
import com.shopsphere.server.dao.AddressDao;
import com.shopsphere.server.dao.AuditDao;
import com.shopsphere.server.dao.CartDao;
import com.shopsphere.server.dao.CategoryDao;
import com.shopsphere.server.dao.InventoryDao;
import com.shopsphere.server.dao.OrderDao;
import com.shopsphere.server.dao.PaymentDao;
import com.shopsphere.server.dao.ProductDao;
import com.shopsphere.server.dao.ReportDao;
import com.shopsphere.server.dao.ReviewDao;
import com.shopsphere.server.dao.SettingsDao;
import com.shopsphere.server.dao.UserDao;
import com.shopsphere.server.dao.WishlistDao;
import com.shopsphere.server.database.Database;
import com.shopsphere.server.network.RequestRouter;
import com.shopsphere.server.network.ShopServer;
import com.shopsphere.server.security.PasswordHasher;
import com.shopsphere.server.security.Pbkdf2PasswordHasher;
import com.shopsphere.server.security.SessionManager;
import com.shopsphere.server.service.AdminService;
import com.shopsphere.server.service.AuditService;
import com.shopsphere.server.service.AuthService;
import com.shopsphere.server.service.BootstrapService;
import com.shopsphere.server.service.CartService;
import com.shopsphere.server.service.CatalogService;
import com.shopsphere.server.service.CheckoutService;
import com.shopsphere.server.service.InventoryService;
import com.shopsphere.server.service.PaymentGateway;
import com.shopsphere.server.service.ReviewService;
import com.shopsphere.server.service.SimulatedPaymentGateway;
import com.shopsphere.server.service.UserAdminService;
import com.shopsphere.server.service.WishlistService;


public final class ServerContext {

    private final ServerConfig config;
    private final Database database;
    private final SessionManager sessions;
    private final RequestRouter router;
    private final BootstrapService bootstrap;
    private final PasswordHasher hasher;

    private ServerContext(ServerConfig config, PaymentGateway gateway) {
        this.config = config;
        this.database = new Database(config);
        this.sessions = new SessionManager(config.sessionIdleMinutes());
        this.hasher = new Pbkdf2PasswordHasher(config.pbkdf2Iterations());

        UserDao users = new UserDao();
        AddressDao addresses = new AddressDao();
        CategoryDao categories = new CategoryDao();
        ProductDao products = new ProductDao();
        InventoryDao inventoryDao = new InventoryDao();
        CartDao carts = new CartDao();
        WishlistDao wishlistDao = new WishlistDao();
        ReviewDao reviewDao = new ReviewDao();
        OrderDao orders = new OrderDao();
        PaymentDao payments = new PaymentDao();
        AuditDao auditDao = new AuditDao();
        SettingsDao settings = new SettingsDao();
        ReportDao reports = new ReportDao();

        AuditService audit = new AuditService(database, auditDao);
        AuthService auth = new AuthService(database, users, addresses, hasher, sessions, audit, config);
        UserAdminService userAdmin = new UserAdminService(database, users, hasher, sessions, audit);
        CatalogService catalog = new CatalogService(database, categories, products, inventoryDao, settings, audit);
        InventoryService inventory = new InventoryService(database, inventoryDao, audit);
        ReviewService reviews = new ReviewService(database, reviewDao, products, orders);
        WishlistService wishlists = new WishlistService(database, wishlistDao, products);
        CartService cart = new CartService(database, carts, products, settings);
        CheckoutService checkout = new CheckoutService(database, carts, addresses, inventoryDao, orders, payments,
                settings, gateway, audit);
        AdminService admin = new AdminService(database, reports, payments, auditDao, settings, audit);
        this.bootstrap = new BootstrapService(database, users, hasher);
        this.router = new RequestRouter(sessions, auth, userAdmin, catalog, inventory, reviews, wishlists, cart,
                checkout, admin);
    }

    public static ServerContext create(ServerConfig config) {
        return new ServerContext(config, new SimulatedPaymentGateway());
    }

    public static ServerContext create(ServerConfig config, PaymentGateway gateway) {
        return new ServerContext(config, gateway);
    }

    public ShopServer newServer() {
        return new ShopServer(config.port(), config.maxClients(), router, sessions);
    }

    public Database database() { return database; }
    public SessionManager sessions() { return sessions; }
    public RequestRouter router() { return router; }
    public BootstrapService bootstrap() { return bootstrap; }
    public PasswordHasher hasher() { return hasher; }
}
