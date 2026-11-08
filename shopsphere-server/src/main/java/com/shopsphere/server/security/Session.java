package com.shopsphere.server.security;

import com.shopsphere.common.protocol.Role;

/** A logged in user. It is bound to the TCP connection that created it. */
public final class Session {
    private final String token;
    private final long userId;
    private final String email;
    private final Role role;
    private final long connectionId;
    private volatile long lastAccessMillis;

    Session(String token, long userId, String email, Role role, long connectionId, long now) {
        this.token = token;
        this.userId = userId;
        this.email = email;
        this.role = role;
        this.connectionId = connectionId;
        this.lastAccessMillis = now;
    }

    public String token() { return token; }
    public long userId() { return userId; }
    public String email() { return email; }
    public Role role() { return role; }
    long connectionId() { return connectionId; }
    long lastAccessMillis() { return lastAccessMillis; }
    void touch(long now) { this.lastAccessMillis = now; }

    @Override
    public String toString() {
        return "Session[user=" + userId + ", role=" + role + "]";   // the token is never printed
    }
}
