package com.shopsphere.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.border.Border;

/** Small factory methods for styled components, so panels stay short and consistent. */
public final class Ui {

    public enum Kind { PRIMARY, SECONDARY, DANGER }

    private Ui() { }

    public static JLabel label(String text, int style, float size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(style, size));
        l.setForeground(color);
        return l;
    }

    public static JLabel title(String text) {
        return label(text, Font.BOLD, 24f, Theme.TEXT);
    }

    public static JLabel muted(String text) {
        return label(text, Font.PLAIN, 13f, Theme.MUTED);
    }

    public static JButton button(String text, Kind kind) {
        JButton b = new RoundButton(text, kind);
        return b;
    }

    public static JButton button(String text) {
        return button(text, Kind.PRIMARY);
    }

    private static final class RoundButton extends JButton {
        private static final long serialVersionUID = 1L;
        private final Kind kind;

        RoundButton(String text, Kind kind) {
            super(text);
            this.kind = kind;
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setFont(Theme.font(Font.BOLD, 13f));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(9, 18, 9, 18));
            setForeground(kind == Kind.SECONDARY ? Theme.TEXT : Color.WHITE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color base = switch (kind) {
                case PRIMARY -> Theme.ACCENT;
                case DANGER -> Theme.DANGER;
                case SECONDARY -> Color.WHITE;
            };
            if (!isEnabled()) base = new Color(0xD1D5DB);
            else if (getModel().isPressed()) base = base.darker();
            else if (getModel().isRollover() && kind != Kind.SECONDARY) base = base.darker();
            else if (getModel().isRollover()) base = new Color(0xF3F4F6);
            g2.setColor(base);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            if (kind == Kind.SECONDARY) {
                g2.setColor(Theme.BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static Border inputBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER), BorderFactory.createEmptyBorder(7, 10, 7, 10));
    }

    public static JTextField field(int columns) {
        JTextField f = new JTextField(columns);
        f.setBorder(inputBorder());
        return f;
    }

    public static JPasswordField password(int columns) {
        JPasswordField f = new JPasswordField(columns);
        f.setBorder(inputBorder());
        return f;
    }

    /** A white rounded-looking panel with a thin border. */
    public static JPanel card() {
        JPanel p = new JPanel();
        p.setBackground(Theme.SURFACE);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER), BorderFactory.createEmptyBorder(16, 16, 16, 16)));
        return p;
    }

    public static JPanel page(String heading, String subtitle) {
        JPanel page = new JPanel(new BorderLayout(0, 16));
        page.setBackground(Theme.BACKGROUND);
        page.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(title(heading), BorderLayout.NORTH);
        if (subtitle != null) head.add(muted(subtitle), BorderLayout.SOUTH);
        page.add(head, BorderLayout.NORTH);
        return page;
    }

    /** A solid coloured tile with initials. Product images are only references, so this is the stand-in picture. */
    public static JComponent imageTile(String name, int size) {
        return new JComponent() {
            private static final long serialVersionUID = 1L;
            { setPreferredSize(new Dimension(size, size)); setMinimumSize(new Dimension(size, size)); }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int hue = Math.abs(name.hashCode()) % 360;
                g2.setColor(Color.getHSBColor(hue / 360f, 0.35f, 0.95f));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.setColor(Color.getHSBColor(hue / 360f, 0.6f, 0.45f));
                g2.setFont(Theme.font(Font.BOLD, size / 3.2f));
                String initials = initials(name);
                int w = g2.getFontMetrics().stringWidth(initials);
                g2.drawString(initials, (getWidth() - w) / 2, getHeight() / 2 + g2.getFontMetrics().getAscent() / 3);
                g2.dispose();
            }
        };
    }

    private static String initials(String name) {
        StringBuilder sb = new StringBuilder();
        for (String part : name.split("\\s+")) {
            if (!part.isEmpty() && Character.isLetterOrDigit(part.charAt(0)) && sb.length() < 2) sb.append(Character.toUpperCase(part.charAt(0)));
        }
        return sb.toString();
    }
}
