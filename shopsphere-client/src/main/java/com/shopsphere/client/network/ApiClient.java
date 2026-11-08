package com.shopsphere.client.network;

import com.shopsphere.common.dto.AdminDtos.AuditLogDto;
import com.shopsphere.common.dto.AdminDtos.AuditQuery;
import com.shopsphere.common.dto.AdminDtos.DashboardDto;
import com.shopsphere.common.dto.AdminDtos.InventoryDto;
import com.shopsphere.common.dto.AdminDtos.ReportDto;
import com.shopsphere.common.dto.AdminDtos.SettingDto;
import com.shopsphere.common.dto.AdminDtos.StockAdjustRequest;
import com.shopsphere.common.dto.AdminDtos.StockMovementDto;
import com.shopsphere.common.dto.AuthDtos.AddressDto;
import com.shopsphere.common.dto.AuthDtos.ChangePasswordRequest;
import com.shopsphere.common.dto.AuthDtos.CreateAdminRequest;
import com.shopsphere.common.dto.AuthDtos.LoginRequest;
import com.shopsphere.common.dto.AuthDtos.LoginResult;
import com.shopsphere.common.dto.AuthDtos.RegisterRequest;
import com.shopsphere.common.dto.AuthDtos.RoleChangeRequest;
import com.shopsphere.common.dto.AuthDtos.UpdateProfileRequest;
import com.shopsphere.common.dto.AuthDtos.UserDto;
import com.shopsphere.common.dto.AuthDtos.UserStatusRequest;
import com.shopsphere.common.dto.CatalogDtos.CategoryDto;
import com.shopsphere.common.dto.CatalogDtos.ProductDto;
import com.shopsphere.common.dto.CatalogDtos.ProductQuery;
import com.shopsphere.common.dto.CatalogDtos.ProductSaveRequest;
import com.shopsphere.common.dto.CatalogDtos.ReviewDto;
import com.shopsphere.common.dto.CatalogDtos.ReviewRequest;
import com.shopsphere.common.dto.ShoppingDtos.CartDto;
import com.shopsphere.common.dto.ShoppingDtos.CartUpdate;
import com.shopsphere.common.dto.ShoppingDtos.CheckoutRequest;
import com.shopsphere.common.dto.ShoppingDtos.CheckoutResult;
import com.shopsphere.common.dto.ShoppingDtos.OrderDto;
import com.shopsphere.common.dto.ShoppingDtos.OrderQuery;
import com.shopsphere.common.dto.ShoppingDtos.OrderStatusRequest;
import com.shopsphere.common.dto.ShoppingDtos.PaymentDto;
import com.shopsphere.common.protocol.Command;
import com.shopsphere.common.protocol.OrderStatus;
import com.shopsphere.common.protocol.ReportType;
import com.shopsphere.common.protocol.Request;
import com.shopsphere.common.protocol.Response;
import com.shopsphere.common.protocol.Role;
import com.shopsphere.common.protocol.UserStatus;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.UUID;

public class ApiClient {

    private final ServerConnection connection;
    private String token;
    private UserDto user;

    public ApiClient(ServerConnection connection) {
        this.connection = connection;
    }

    public UserDto user() { return user; }
    public Role role() { return user == null ? null : user.role(); }
    public boolean isAdmin() { return user != null && user.role().isAdmin(); }
    public boolean isSuperAdmin() { return user != null && user.role() == Role.SUPER_ADMIN; }

    @SuppressWarnings("unchecked")
    private <T> T call(Command command, Serializable payload) throws ApiException {
        Response r = connection.send(new Request(UUID.randomUUID().toString(), command, token, payload));
        if (!r.ok()) throw new ApiException(r.code(), r.message());
        return (T) r.data();
    }

    // account
    public UserDto register(RegisterRequest r) throws ApiException { return call(Command.REGISTER, r); }

    public UserDto login(String email, String password) throws ApiException {
        LoginResult result = call(Command.LOGIN, new LoginRequest(email, password));
        token = result.token();
        user = result.user();
        return user;
    }

    public void logout() {
        try {
            if (token != null) call(Command.LOGOUT, null);
        } catch (ApiException e) {
            // the session is dropped locally either way
            token = null;
        }
        token = null;
        user = null;
    }

    public UserDto profile() throws ApiException { user = call(Command.GET_PROFILE, null); return user; }
    public UserDto updateProfile(UpdateProfileRequest r) throws ApiException { user = call(Command.UPDATE_PROFILE, r); return user; }
    public void changePassword(String current, String next) throws ApiException {
        call(Command.CHANGE_PASSWORD, new ChangePasswordRequest(current, next));
    }
    public ArrayList<AddressDto> addresses() throws ApiException { return call(Command.LIST_ADDRESSES, null); }
    public AddressDto addAddress(AddressDto a) throws ApiException { return call(Command.ADD_ADDRESS, a); }
    public ArrayList<AddressDto> deleteAddress(long id) throws ApiException { return call(Command.DELETE_ADDRESS, id); }

    // catalogue
    public ArrayList<CategoryDto> categories() throws ApiException { return call(Command.LIST_CATEGORIES, null); }
    public ArrayList<ProductDto> products(String search, Long categoryId, boolean includeInactive) throws ApiException {
        return call(Command.LIST_PRODUCTS, new ProductQuery(search, categoryId, includeInactive));
    }
    public ProductDto product(long id) throws ApiException { return call(Command.GET_PRODUCT, id); }
    public ArrayList<ReviewDto> reviews(long productId) throws ApiException { return call(Command.LIST_REVIEWS, productId); }
    public ReviewDto addReview(long productId, int rating, String comment) throws ApiException {
        return call(Command.ADD_REVIEW, new ReviewRequest(productId, rating, comment));
    }

    // cart and wishlist
    public CartDto cart() throws ApiException { return call(Command.GET_CART, null); }
    public CartDto addToCart(long productId, int qty) throws ApiException { return call(Command.ADD_TO_CART, new CartUpdate(productId, qty)); }
    public CartDto setCartQuantity(long productId, int qty) throws ApiException { return call(Command.UPDATE_CART_ITEM, new CartUpdate(productId, qty)); }
    public CartDto removeFromCart(long productId) throws ApiException { return call(Command.REMOVE_CART_ITEM, productId); }
    public ArrayList<ProductDto> wishlist() throws ApiException { return call(Command.GET_WISHLIST, null); }
    public ArrayList<ProductDto> addWishlist(long productId) throws ApiException { return call(Command.ADD_WISHLIST, productId); }
    public ArrayList<ProductDto> removeWishlist(long productId) throws ApiException { return call(Command.REMOVE_WISHLIST, productId); }

    // orders
    public CheckoutResult checkout(long addressId, String card, String key) throws ApiException {
        return call(Command.CHECKOUT, new CheckoutRequest(addressId, card, key));
    }
    public ArrayList<OrderDto> myOrders() throws ApiException { return call(Command.LIST_MY_ORDERS, null); }
    public OrderDto order(long id) throws ApiException { return call(Command.GET_ORDER, id); }
    public OrderDto cancelOrder(long id) throws ApiException { return call(Command.CANCEL_ORDER, id); }
    public String receipt(long id) throws ApiException { return call(Command.GET_RECEIPT, id); }

    // administration
    public DashboardDto dashboard() throws ApiException { return call(Command.GET_DASHBOARD, null); }
    public ArrayList<UserDto> customers(String search) throws ApiException { return call(Command.LIST_USERS, search); }
    public UserDto setUserStatus(long id, UserStatus status) throws ApiException { return call(Command.SET_USER_STATUS, new UserStatusRequest(id, status)); }
    public ProductDto saveProduct(ProductSaveRequest r) throws ApiException { return call(Command.SAVE_PRODUCT, r); }
    public ProductDto archiveProduct(long id) throws ApiException { return call(Command.ARCHIVE_PRODUCT, id); }
    public CategoryDto saveCategory(CategoryDto c) throws ApiException { return call(Command.SAVE_CATEGORY, c); }
    public ArrayList<CategoryDto> deleteCategory(long id) throws ApiException { return call(Command.DELETE_CATEGORY, id); }
    public ArrayList<OrderDto> allOrders(OrderStatus status, String search) throws ApiException { return call(Command.LIST_ALL_ORDERS, new OrderQuery(status, search)); }
    public OrderDto setOrderStatus(long id, OrderStatus status) throws ApiException { return call(Command.UPDATE_ORDER_STATUS, new OrderStatusRequest(id, status)); }
    public ArrayList<InventoryDto> inventory() throws ApiException { return call(Command.LIST_INVENTORY, null); }
    public InventoryDto adjustStock(StockAdjustRequest r) throws ApiException { return call(Command.ADJUST_STOCK, r); }
    public ArrayList<StockMovementDto> movements(Long productId) throws ApiException { return call(Command.LIST_STOCK_MOVEMENTS, productId); }
    public ArrayList<PaymentDto> payments() throws ApiException { return call(Command.LIST_PAYMENTS, null); }
    public ReportDto report(ReportType type) throws ApiException { return call(Command.GET_REPORT, type); }
    public ArrayList<AuditLogDto> audit(int limit) throws ApiException { return call(Command.LIST_AUDIT_LOGS, new AuditQuery(limit)); }

    // super administrator
    public ArrayList<UserDto> admins() throws ApiException { return call(Command.LIST_ADMINS, null); }
    public UserDto createAdmin(CreateAdminRequest r) throws ApiException { return call(Command.CREATE_ADMIN, r); }
    public UserDto setRole(long id, Role role) throws ApiException { return call(Command.SET_USER_ROLE, new RoleChangeRequest(id, role)); }
    public ArrayList<SettingDto> settings() throws ApiException { return call(Command.LIST_SETTINGS, null); }
    public SettingDto updateSetting(String key, String value) throws ApiException { return call(Command.UPDATE_SETTING, new SettingDto(key, value, null)); }
}
