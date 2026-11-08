package com.shopsphere.client.ui;

import java.awt.Color;
import java.awt.Font;
import javax.swing.UIManager;

/** Colours and fonts used everywhere, so the whole app looks like one product. */
public final class Theme {

    public static final Color SIDEBAR = new Color(0x111827);
    public static final Color SIDEBAR_HOVER = new Color(0x1F2937);
    public static final Color SIDEBAR_TEXT = new Color(0x9CA3AF);
    public static final Color ACCENT = new Color(0x4F46E5);
    public static final Color ACCENT_DARK = new Color(0x4338CA);
    public static final Color BACKGROUND = new Color(0xF3F4F6);
    public static final Color SURFACE = Color.WHITE;
    public static final Color BORDER = new Color(0xE5E7EB);
    public static final Color TEXT = new Color(0x111827);
    public static final Color MUTED = new Color(0x6B7280);
    public static final Color DANGER = new Color(0xDC2626);
    public static final Color SUCCESS = new Color(0x059669);
    public static final Color WARNING = new Color(0xD97706);

    public static final String FONT_FAMILY = "SansSerif";

    private Theme() { }

    public static Font font(int style, float size) {
        return new Font(FONT_FAMILY, style, 1).deriveFont(style, size);
    }

    public static void install() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception e) {
            // the default look is fine if the cross platform one cannot be set
            return;
        }
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("OptionPane.background", SURFACE);
        UIManager.put("OptionPane.messageFont", font(Font.PLAIN, 14));
        UIManager.put("Label.font", font(Font.PLAIN, 14));
        UIManager.put("Button.font", font(Font.BOLD, 13));
        UIManager.put("TextField.font", font(Font.PLAIN, 14));
        UIManager.put("PasswordField.font", font(Font.PLAIN, 14));
        UIManager.put("TextArea.font", font(Font.PLAIN, 14));
        UIManager.put("ComboBox.font", font(Font.PLAIN, 14));
        UIManager.put("Table.font", font(Font.PLAIN, 13));
        UIManager.put("TableHeader.font", font(Font.BOLD, 12));
        UIManager.put("Spinner.font", font(Font.PLAIN, 14));
        UIManager.put("TabbedPane.font", font(Font.BOLD, 13));
    }
}
