package com.shopsphere.server.network;

import com.shopsphere.common.dto.AdminDtos.AuditQuery;
import com.shopsphere.common.dto.AdminDtos.SettingDto;
import com.shopsphere.common.dto.AdminDtos.StockAdjustRequest;
import com.shopsphere.common.dto.AuthDtos.AddressDto;
import com.shopsphere.common.dto.AuthDtos.ChangePasswordRequest;
import com.shopsphere.common.dto.AuthDtos.CreateAdminRequest;
import com.shopsphere.common.dto.AuthDtos.LoginRequest;
import com.shopsphere.common.dto.AuthDtos.RegisterRequest;
import com.shopsphere.common.dto.AuthDtos.RoleChangeRequest;
import com.shopsphere.common.dto.AuthDtos.UpdateProfileRequest;
import com.shopsphere.common.dto.AuthDtos.UserStatusRequest;
import com.shopsphere.common.dto.CatalogDtos.CategoryDto;
import com.shopsphere.common.dto.CatalogDtos.ProductQuery;
import com.shopsphere.common.dto.CatalogDtos.ProductSaveRequest;
import com.shopsphere.common.dto.CatalogDtos.ReviewRequest;
import com.shopsphere.common.dto.ShoppingDtos.CartUpdate;
import com.shopsphere.common.dto.ShoppingDtos.CheckoutRequest;
import com.shopsphere.common.dto.ShoppingDtos.OrderQuery;
import com.shopsphere.common.dto.ShoppingDtos.OrderStatusRequest;
import com.shopsphere.common.protocol.Command;
import com.shopsphere.common.protocol.ErrorCode;
import com.shopsphere.common.protocol.ReportType;
import com.shopsphere.common.protocol.Request;
import com.shopsphere.common.protocol.Response;
import com.shopsphere.server.security.CommandPolicy;
import com.shopsphere.server.security.Session;
import com.shopsphere.server.security.SessionManager;
import com.shopsphere.server.service.AdminService;
import com.shopsphere.server.service.AuthService;
import com.shopsphere.server.service.CartService;
import com.shopsphere.server.service.CatalogService;
import com.shopsphere.server.service.CheckoutService;
import com.shopsphere.server.service.InventoryService;
import com.shopsphere.server.service.ReviewService;
import com.shopsphere.server.service.ShopException;
import com.shopsphere.server.service.UserAdminService;
import com.shopsphere.server.service.WishlistService;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Turns one request into one response. Every command goes through CommandPolicy first, so a command
 * with no rule, or one the role does not allow, is refused before any service code runs.
 */
public class RequestRouter {

    private static final Logger LOG = Logger.getLogger(RequestRouter.class.getName());

    private final SessionManager sessions;
    private final AuthService auth;
    private final UserAdminService userAdmin;
    private final CatalogService catalog;
    private final InventoryService inventory;
    private final ReviewService reviews;
    private final WishlistService wishlists;
    private final CartService cart;
    private final CheckoutService checkout;
    private final AdminService admin;

    public RequestRouter(SessionManager sessions, AuthService auth, UserAdminService userAdmin, CatalogService catalog,
                         InventoryService inventory, ReviewService reviews, WishlistService wishlists,
                         CartService cart, CheckoutService checkout, AdminService admin) {
        this.sessions = sessions;
        this.auth = auth;
        this.userAdmin = userAdmin;
        this.catalog = catalog;
        this.inventory = inventory;
        this.reviews = reviews;
        this.wishlists = wishlists;
        this.cart = cart;
        this.checkout = checkout;
        this.admin = admin;
    }

    public Response handle(Request req, long connectionId) {
        String id = req == null ? null : req.requestId();
        try {
            if (req == null || req.command() == null) {
                return Response.failure(id, ErrorCode.BAD_REQUEST, "Invalid request");
            }
            Session session = null;
            if (!CommandPolicy.isPublic(req.command())) {
                session = sessions.resolve(req.token(), connectionId)
                        .orElseThrow(() -> new ShopException(ErrorCode.UNAUTHENTICATED, "Please log in"));
            }
            CommandPolicy.check(req.command(), session);
            Serializable data = dispatch(req, session, connectionId);
            return Response.success(id, data);
        } catch (ShopException e) {
            return Response.failure(id, e.code(), e.getMessage());
        } catch (ClassCastException e) {
            return Response.failure(id, ErrorCode.BAD_REQUEST, "Invalid request data");
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Unexpected error handling " + req.command(), e);
            return Response.failure(id, ErrorCode.INTERNAL_ERROR, "Something went wrong on the server. Please try again");
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T body(Request r) {
        return (T) r.payload();
    }

    private static long id(Request r) {
        if (!(r.payload() instanceof Long l)) throw ShopException.invalid("An id is required");
        return l;
    }

    private Serializable dispatch(Request r, Session s, long conn) {
        return switch (r.command()) {
            case REGISTER -> auth.register((RegisterRequest) r.payload());
            case LOGIN -> auth.login((LoginRequest) r.payload(), conn);
            case LOGOUT -> { auth.logout(s); yield null; }
            case GET_PROFILE -> auth.profile(s);
            case UPDATE_PROFILE -> auth.updateProfile(s, (UpdateProfileRequest) r.payload());
            case CHANGE_PASSWORD -> { auth.changePassword(s, (ChangePasswordRequest) r.payload()); yield null; }
            case LIST_ADDRESSES -> auth.listAddresses(s);
            case ADD_ADDRESS -> auth.addAddress(s, (AddressDto) r.payload());
            case DELETE_ADDRESS -> { auth.deleteAddress(s, id(r)); yield auth.listAddresses(s); }

            case LIST_CATEGORIES -> catalog.listCategories();
            case LIST_PRODUCTS -> catalog.listProducts((ProductQuery) r.payload(), s == null ? null : s.role());
            case GET_PRODUCT -> catalog.getProduct(id(r), s == null ? null : s.role());
            case LIST_REVIEWS -> reviews.list(id(r));
            case ADD_REVIEW -> reviews.add(s, (ReviewRequest) r.payload());

            case GET_CART -> cart.view(s);
            case ADD_TO_CART -> cart.add(s, (CartUpdate) r.payload());
            case UPDATE_CART_ITEM -> cart.update(s, (CartUpdate) r.payload());
            case REMOVE_CART_ITEM -> cart.remove(s, id(r));
            case GET_WISHLIST -> wishlists.get(s);
            case ADD_WISHLIST -> wishlists.add(s, id(r));
            case REMOVE_WISHLIST -> wishlists.remove(s, id(r));

            case CHECKOUT -> checkout.checkout(s, (CheckoutRequest) r.payload());
            case LIST_MY_ORDERS -> checkout.myOrders(s);
            case GET_ORDER -> checkout.get(s, id(r));
            case CANCEL_ORDER -> checkout.cancel(s, id(r));
            case GET_RECEIPT -> checkout.receipt(s, id(r));

            case GET_DASHBOARD -> admin.dashboard();
            case LIST_USERS -> userAdmin.listCustomers((String) r.payload());
            case SET_USER_STATUS -> userAdmin.setStatus(s, (UserStatusRequest) r.payload());
            case SAVE_PRODUCT -> catalog.saveProduct(s, (ProductSaveRequest) r.payload());
            case ARCHIVE_PRODUCT -> catalog.archiveProduct(s, id(r));
            case SAVE_CATEGORY -> catalog.saveCategory(s, (CategoryDto) r.payload());
            case DELETE_CATEGORY -> { catalog.deleteCategory(s, id(r)); yield catalog.listCategories(); }
            case LIST_ALL_ORDERS -> checkout.listAll((OrderQuery) r.payload());
            case UPDATE_ORDER_STATUS -> checkout.updateStatus(s, (OrderStatusRequest) r.payload());
            case LIST_INVENTORY -> inventory.list();
            case ADJUST_STOCK -> inventory.adjust(s, (StockAdjustRequest) r.payload());
            case LIST_STOCK_MOVEMENTS -> inventory.movements((Long) r.payload());
            case LIST_PAYMENTS -> admin.payments();
            case GET_REPORT -> admin.report((ReportType) r.payload());
            case LIST_AUDIT_LOGS -> admin.auditLog(s, (AuditQuery) r.payload());

            case LIST_ADMINS -> userAdmin.listAdmins(s);
            case CREATE_ADMIN -> userAdmin.createAdmin(s, (CreateAdminRequest) r.payload());
            case SET_USER_ROLE -> userAdmin.setRole(s, (RoleChangeRequest) r.payload());
            case LIST_SETTINGS -> admin.listSettings();
            case UPDATE_SETTING -> admin.updateSetting(s, (SettingDto) r.payload());
        };
    }
}
