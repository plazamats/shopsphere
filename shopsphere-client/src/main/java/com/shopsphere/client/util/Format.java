package com.shopsphere.client.util;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class Format {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm").withZone(ZoneId.systemDefault());

    private Format() { }

    public static String money(BigDecimal v) {
        return v == null ? "" : String.format("R %,.2f", v).replace(',', ' ');
    }

    public static String date(Instant t) {
        return t == null ? "" : DATE_TIME.format(t);
    }

    /** "ORDER_STATUS" becomes "Order status". */
    public static String nice(Enum<?> e) {
        if (e == null) return "";
        String s = e.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Escape text before it is placed inside an HTML label, so user text can never be treated as markup. */
    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    public static String stars(int n) {
        return "★".repeat(Math.max(0, n)) + "☆".repeat(Math.max(0, 5 - n));
    }
}
