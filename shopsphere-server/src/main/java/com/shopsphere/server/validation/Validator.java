package com.shopsphere.server.validation;

import com.shopsphere.server.service.ShopException;
import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Server-side input validation. The client may also check input, but the server never trusts it.
 * Every method returns the cleaned value or throws a ShopException with a safe message.
 */
public final class Validator {

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]{1,190}\\.[A-Za-z]{2,24}$");
    private static final Pattern NAME = Pattern.compile("^[\\p{L}][\\p{L} .'-]{0,59}$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+() -]{7,20}$");
    private static final Pattern SKU = Pattern.compile("^[A-Za-z0-9-]{3,30}$");
    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9-]{8,64}$");
    private static final Pattern POSTAL = Pattern.compile("^[A-Za-z0-9 -]{3,12}$");
    private static final BigDecimal MAX_PRICE = new BigDecimal("1000000.00");

    private Validator() { }

    public static String email(String value) {
        String v = required(value, "Email").toLowerCase();
        if (v.length() > 254 || !EMAIL.matcher(v).matches()) throw ShopException.invalid("Enter a valid email address");
        return v;
    }

    /** At least 8 characters with upper case, lower case and a digit. */
    public static String password(String value, String email) {
        if (value == null || value.length() < 8 || value.length() > 100) {
            throw ShopException.invalid("Password must be 8 to 100 characters");
        }
        boolean upper = false, lower = false, digit = false;
        for (char ch : value.toCharArray()) {
            if (Character.isUpperCase(ch)) upper = true;
            else if (Character.isLowerCase(ch)) lower = true;
            else if (Character.isDigit(ch)) digit = true;
        }
        if (!(upper && lower && digit)) {
            throw ShopException.invalid("Password needs an upper case letter, a lower case letter and a digit");
        }
        if (email != null && value.equalsIgnoreCase(email)) throw ShopException.invalid("Password must not be your email");
        return value;
    }

    public static String personName(String value, String field) {
        String v = required(value, field);
        if (!NAME.matcher(v).matches()) throw ShopException.invalid(field + " contains invalid characters");
        return v;
    }

    /** Optional phone number. Returns null when empty. */
    public static String phone(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        if (!PHONE.matcher(v).matches()) throw ShopException.invalid("Enter a valid phone number");
        return v;
    }

    public static String sku(String value) {
        String v = required(value, "SKU");
        if (!SKU.matcher(v).matches()) throw ShopException.invalid("SKU must be 3 to 30 letters, digits or hyphens");
        return v.toUpperCase();
    }

    /** Required free text with a maximum length. Control characters are rejected. */
    public static String text(String value, String field, int maxLength) {
        String v = required(value, field);
        return checkLength(v, field, maxLength);
    }

    /** Optional free text. Returns null when empty. */
    public static String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) return null;
        return checkLength(value.trim(), field, maxLength);
    }

    public static BigDecimal price(BigDecimal value) {
        if (value == null) throw ShopException.invalid("Price is required");
        if (value.signum() <= 0) throw ShopException.invalid("Price must be greater than zero");
        if (value.scale() > 2 && value.stripTrailingZeros().scale() > 2) throw ShopException.invalid("Price can have at most 2 decimals");
        if (value.compareTo(MAX_PRICE) > 0) throw ShopException.invalid("Price is too large");
        return value.setScale(2, java.math.RoundingMode.UNNECESSARY);
    }

    public static int quantity(int value, int max) {
        if (value < 1) throw ShopException.invalid("Quantity must be at least 1");
        if (value > max) throw ShopException.invalid("Quantity cannot be more than " + max);
        return value;
    }

    public static int rating(int value) {
        if (value < 1 || value > 5) throw ShopException.invalid("Rating must be from 1 to 5");
        return value;
    }

    public static int nonNegative(int value, String field) {
        if (value < 0) throw ShopException.invalid(field + " cannot be negative");
        return value;
    }

    /** An image reference is a relative path or an http(s) link. Other schemes are refused. */
    public static String imageRef(String value) {
        String v = required(value, "Image reference");
        if (v.length() > 500) throw ShopException.invalid("Image reference is too long");
        String lower = v.toLowerCase();
        boolean ok = lower.startsWith("http://") || lower.startsWith("https://") || !lower.contains(":");
        if (!ok || lower.contains("..") ) throw ShopException.invalid("Image reference is not allowed");
        return v;
    }

    public static String idempotencyKey(String value) {
        if (value == null || !KEY.matcher(value).matches()) throw ShopException.invalid("Invalid request key");
        return value;
    }

    /** Remove spaces and hyphens, then check digits and the Luhn checksum. Returns the digits only. */
    public static String cardNumber(String value) {
        if (value == null) throw ShopException.invalid("Card number is required");
        String digits = value.replaceAll("[ -]", "");
        if (!digits.matches("\\d{13,19}")) throw ShopException.invalid("Enter a valid card number");
        int sum = 0;
        boolean alt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = digits.charAt(i) - '0';
            if (alt) { n *= 2; if (n > 9) n -= 9; }
            sum += n;
            alt = !alt;
        }
        if (sum % 10 != 0) throw ShopException.invalid("Enter a valid card number");
        return digits;
    }

    public static String postalCode(String value) {
        String v = required(value, "Postal code");
        if (!POSTAL.matcher(v).matches()) throw ShopException.invalid("Enter a valid postal code");
        return v;
    }

    /** Search text is trimmed, limited in length and may be null. */
    public static String search(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        if (v.length() > 100) v = v.substring(0, 100);
        return v;
    }

    public static String required(String value, String field) {
        if (value == null || value.isBlank()) throw ShopException.invalid(field + " is required");
        return value.trim();
    }

    private static String checkLength(String v, String field, int max) {
        if (v.length() > max) throw ShopException.invalid(field + " must be at most " + max + " characters");
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            if (Character.isISOControl(ch) && ch != '\n' && ch != '\r' && ch != '\t') {
                throw ShopException.invalid(field + " contains invalid characters");
            }
        }
        return v;
    }
}
