package com.shopsphere.client.ui;

import com.shopsphere.client.network.ApiClient;
import com.shopsphere.client.network.ServerConnection;
import com.shopsphere.client.util.Async;
import com.shopsphere.common.dto.AuthDtos.RegisterRequest;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/** The first screen: log in or create a customer account. */
public class LoginFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private final transient ApiClient api;
    private final transient ServerConnection connection;
    private final CardLayout cards = new CardLayout();
    private final JPanel formArea = new JPanel(cards);

    private final JTextField loginEmail = Ui.field(22);
    private final JPasswordField loginPassword = Ui.password(22);
    private final JLabel loginMessage = Ui.label(" ", Font.PLAIN, 13f, Theme.DANGER);

    private final JTextField regFirst = Ui.field(10);
    private final JTextField regLast = Ui.field(10);
    private final JTextField regEmail = Ui.field(22);
    private final JTextField regPhone = Ui.field(22);
    private final JPasswordField regPassword = Ui.password(22);
    private final JLabel regMessage = Ui.label(" ", Font.PLAIN, 13f, Theme.DANGER);

    public LoginFrame(ApiClient api, ServerConnection connection) {
        super("ShopSphere");
        this.api = api;
        this.connection = connection;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { connection.close(); dispose(); System.exit(0); }
        });
        setLayout(new GridLayout(1, 2));
        add(brandPanel());
        formArea.setBackground(Theme.SURFACE);
        formArea.add(loginForm(), "login");
        formArea.add(registerForm(), "register");
        add(formArea);
        setSize(980, 640);
        setMinimumSize(new Dimension(820, 600));
        setLocationRelativeTo(null);
    }

    private JPanel brandPanel() {
        JPanel p = new JPanel();
        p.setBackground(Theme.SIDEBAR);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(80, 56, 56, 56));
        p.add(Ui.label("ShopSphere", Font.BOLD, 40f, Color.WHITE));
        p.add(Box.createVerticalStrut(14));
        p.add(Ui.label("<html>Everything you need,<br>delivered to your door.</html>", Font.PLAIN, 20f, new Color(0xC7D2FE)));
        p.add(Box.createVerticalGlue());
        p.add(Ui.label("<html>Payments in this version are a simulation.<br>No real money is ever charged.</html>",
                Font.PLAIN, 13f, Theme.SIDEBAR_TEXT));
        return p;
    }

    private JPanel centered(JPanel inner) {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBackground(Theme.SURFACE);
        outer.add(inner, new GridBagConstraints());
        return outer;
    }

    private JPanel column() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }

    private void addRow(JPanel p, String caption, java.awt.Component field) {
        JLabel l = Ui.label(caption, Font.BOLD, 12f, Theme.MUTED);
        l.setAlignmentX(LEFT_ALIGNMENT);
        ((javax.swing.JComponent) field).setAlignmentX(LEFT_ALIGNMENT);
        p.add(l);
        p.add(Box.createVerticalStrut(4));
        p.add(field);
        p.add(Box.createVerticalStrut(14));
    }

    private JPanel loginForm() {
        JPanel p = column();
        JLabel h = Ui.title("Welcome back");
        h.setAlignmentX(LEFT_ALIGNMENT);
        p.add(h);
        JLabel sub = Ui.muted("Log in to continue shopping");
        sub.setAlignmentX(LEFT_ALIGNMENT);
        p.add(sub);
        p.add(Box.createVerticalStrut(24));
        addRow(p, "EMAIL", loginEmail);
        addRow(p, "PASSWORD", loginPassword);
        loginMessage.setAlignmentX(LEFT_ALIGNMENT);
        p.add(loginMessage);
        p.add(Box.createVerticalStrut(8));
        JButton login = Ui.button("Log in");
        login.setAlignmentX(LEFT_ALIGNMENT);
        login.addActionListener(e -> doLogin(login));
        loginPassword.addActionListener(e -> doLogin(login));
        p.add(login);
        p.add(Box.createVerticalStrut(16));
        JButton toRegister = Ui.button("Create a customer account", Ui.Kind.SECONDARY);
        toRegister.setAlignmentX(LEFT_ALIGNMENT);
        toRegister.addActionListener(e -> cards.show(formArea, "register"));
        p.add(toRegister);
        return centered(p);
    }

    private JPanel registerForm() {
        JPanel p = column();
        JLabel h = Ui.title("Create account");
        h.setAlignmentX(LEFT_ALIGNMENT);
        p.add(h);
        p.add(Box.createVerticalStrut(18));
        JPanel names = new JPanel(new GridLayout(1, 2, 12, 0));
        names.setOpaque(false);
        names.add(regFirst);
        names.add(regLast);
        addRow(p, "FIRST AND LAST NAME", names);
        addRow(p, "EMAIL", regEmail);
        addRow(p, "PHONE (OPTIONAL)", regPhone);
        addRow(p, "PASSWORD (8+ CHARACTERS, LETTERS AND NUMBERS)", regPassword);
        regMessage.setAlignmentX(LEFT_ALIGNMENT);
        p.add(regMessage);
        JButton create = Ui.button("Create account");
        create.setAlignmentX(LEFT_ALIGNMENT);
        create.addActionListener(e -> doRegister(create));
        p.add(create);
        p.add(Box.createVerticalStrut(12));
        JButton back = Ui.button("Back to log in", Ui.Kind.SECONDARY);
        back.setAlignmentX(LEFT_ALIGNMENT);
        back.addActionListener(e -> cards.show(formArea, "login"));
        p.add(back);
        return centered(p);
    }

    private void doLogin(JButton button) {
        loginMessage.setText(" ");
        button.setEnabled(false);
        String email = loginEmail.getText().trim();
        String password = new String(loginPassword.getPassword());
        Async.run(this, () -> api.login(email, password), user -> {
            loginPassword.setText("");
            new MainFrame(api, connection).setVisible(true);
            dispose();
        }, err -> {
            button.setEnabled(true);
            loginMessage.setText("<html>" + err.getMessage() + "</html>");
        });
    }

    private void doRegister(JButton button) {
        regMessage.setText(" ");
        button.setEnabled(false);
        RegisterRequest r = new RegisterRequest(regEmail.getText().trim(), new String(regPassword.getPassword()),
                regFirst.getText().trim(), regLast.getText().trim(), regPhone.getText().trim().isEmpty() ? null : regPhone.getText().trim());
        Async.run(this, () -> api.register(r), user -> {
            button.setEnabled(true);
            regPassword.setText("");
            loginEmail.setText(user.email());
            loginMessage.setText(" ");
            cards.show(formArea, "login");
            Async.showInfo(this, "Account created. You can log in now.");
        }, err -> {
            button.setEnabled(true);
            regMessage.setText("<html>" + err.getMessage() + "</html>");
        });
    }
}
