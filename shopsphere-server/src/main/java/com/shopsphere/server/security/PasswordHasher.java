package com.shopsphere.server.security;

/** Hashes passwords. Version 2 can swap this for BCrypt (Spring Security) without touching the services. */
public interface PasswordHasher {
    String hash(String plainPassword);

    boolean verify(String plainPassword, String storedHash);
}
