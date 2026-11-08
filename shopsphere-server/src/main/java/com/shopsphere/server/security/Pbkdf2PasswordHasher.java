package com.shopsphere.server.security;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * PBKDF2 with HMAC-SHA256 from the JDK: a random salt for every password and a high iteration count.
 * Stored format: pbkdf2_sha256$iterations$salt$hash (Base64).
 * BCrypt was not available in the build environment, and PBKDF2 is an accepted NIST standard.
 */
public class Pbkdf2PasswordHasher implements PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2_sha256";
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private final int iterations;
    private final SecureRandom random = new SecureRandom();

    public Pbkdf2PasswordHasher(int iterations) {
        if (iterations < 1000) throw new IllegalArgumentException("iterations too low");
        this.iterations = iterations;
    }

    @Override
    public String hash(String plainPassword) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = derive(plainPassword.toCharArray(), salt, iterations);
        Base64.Encoder enc = Base64.getEncoder();
        return PREFIX + "$" + iterations + "$" + enc.encodeToString(salt) + "$" + enc.encodeToString(hash);
    }

    @Override
    public boolean verify(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null) return false;
        String[] parts = storedHash.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) return false;
        try {
            int iter = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(plainPassword.toCharArray(), salt, iter);
            return MessageDigest.isEqual(expected, actual);   // constant time
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int iter) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iter, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Password hashing is not available", e);
        } finally {
            spec.clearPassword();
        }
    }
}
