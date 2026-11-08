package com.shopsphere.client.ui;

import com.shopsphere.client.network.ApiClient;
import com.shopsphere.client.network.ServerConnection;
import com.shopsphere.client.ui.admin.AdminsPanel;
import com.shopsphere.client.ui.admin.AuditPanel;
import com.shopsphere.client.ui.admin.CategoriesPanel;
import com.shopsphere.client.ui.admin.CustomersPanel;
import com.shopsphere.client.ui.admin.DashboardPanel;
import com.shopsphere.client.ui.admin.InventoryPanel;
import com.shopsphere.client.ui.admin.OrdersAdminPanel;
import com.shopsphere.client.ui.admin.PaymentsPanel;
import com.shopsphere.client.ui.admin.ProductsAdminPanel;
import com.shopsphere.client.ui.admin.ReportsPanel;
import com.shopsphere.client.ui.admin.SettingsPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** The main window: a dark sidebar and a content area. Customers and administrators get different menus. */
public class MainFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private final transient ApiClient api;
    private final transient ServerConnection connection;
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JPanel sidebar = new JPanel();
    private final transient List<JLabel> navItems = new ArrayList<>();
    private final transient List<JComponent> screens = new ArrayList<>();

    public MainFrame(ApiClient api, ServerConnection connection) {
        super("ShopSphere");
        this.api = api;
        this.connection = connection;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { exit(); }
        });
        sidebar.setBackground(Theme.SIDEBAR);
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(220, 0));
        sidebar.setBorder(BorderFactory.createEmptyBorder(24, 12, 16, 12));
        sidebar.add(Ui.label("  ShopSphere", Font.BOLD, 22f, Color.WHITE));
        sidebar.add(Box.createVerticalStrut(28));
        buildMenu();
        sidebar.add(Box.createVerticalGlue());
        sidebar.add(Ui.label("  " + api.user().fullName(), Font.BOLD, 13f, Color.WHITE));
        sidebar.add(Ui.label("  " + Formatting.role(api.user().role()), Font.PLAIN, 12f, Theme.SIDEBAR_TEXT));
        sidebar.add(Box.createVerticalStrut(10));
        addNav("Log out", null, this::logout);
        content.setBackground(Theme.BACKGROUND);
        setLayout(new BorderLayout());
        add(sidebar, BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
        setSize(1240, 780);
        setMinimumSize(new Dimension(1000, 640));
        setLocationRelativeTo(null);
        if (!screens.isEmpty()) show(0);
    }

    /** Small helper so the role label reads well. */
    private static final class Formatting {
        static String role(com.shopsphere.common.protocol.Role r) {
            return com.shopsphere.client.util.Format.nice(r);
        }
    }

    private void buildMenu() {
        if (api.isAdmin()) {
            screen("Dashboard", new DashboardPanel(api));
            screen("Customers", new CustomersPanel(api));
            screen("Products", new ProductsAdminPanel(api));
            screen("Categories", new CategoriesPanel(api));
            screen("Orders", new OrdersAdminPanel(api));
            screen("Inventory", new InventoryPanel(api));
            screen("Payments", new PaymentsPanel(api));
            screen("Reports", new ReportsPanel(api));
            screen("Audit log", new AuditPanel(api));
            if (api.isSuperAdmin()) {
                screen("Administrators", new AdminsPanel(api));
                screen("Settings", new SettingsPanel(api));
            }
        } else {
            screen("Shop", new CatalogPanel(api, this::showByName));
            screen("Wishlist", new WishlistPanel(api));
            screen("Cart", new CartPanel(api, this::showByName));
            screen("My orders", new OrdersPanel(api));
            screen("Profile", new ProfilePanel(api));
        }
    }

    private void screen(String name, JComponent panel) {
        int index = screens.size();
        screens.add(panel);
        content.add(panel, name);
        addNav(name, name, () -> show(index));
    }

    private void addNav(String text, String key, Runnable action) {
        JLabel item = Ui.label("  " + text, Font.PLAIN, 14f, Theme.SIDEBAR_TEXT);
        item.setOpaque(true);
        item.setBackground(Theme.SIDEBAR);
        item.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        item.setAlignmentX(LEFT_ALIGNMENT);
        item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        item.setName(key);
        item.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { action.run(); }
            @Override public void mouseEntered(MouseEvent e) { if (item.getBackground() != Theme.ACCENT) item.setBackground(Theme.SIDEBAR_HOVER); }
            @Override public void mouseExited(MouseEvent e) { if (item.getBackground() != Theme.ACCENT) item.setBackground(Theme.SIDEBAR); }
        });
        if (key != null) navItems.add(item);
        sidebar.add(item);
        sidebar.add(Box.createVerticalStrut(2));
    }

    private void showByName(String name) {
        for (int i = 0; i < navItems.size(); i++) {
            if (name.equals(navItems.get(i).getName())) { show(i); return; }
        }
    }

    private void show(int index) {
        for (int i = 0; i < navItems.size(); i++) {
            boolean on = i == index;
            navItems.get(i).setBackground(on ? Theme.ACCENT : Theme.SIDEBAR);
            navItems.get(i).setForeground(on ? Color.WHITE : Theme.SIDEBAR_TEXT);
        }
        cards.show(content, navItems.get(index).getName());
        if (screens.get(index) instanceof Refreshable r) r.refresh();
    }

    private void logout() {
        api.logout();
        new LoginFrame(api, connection).setVisible(true);
        dispose();
    }

    private void exit() {
        api.logout();
        connection.close();
        dispose();
        System.exit(0);
    }
}
