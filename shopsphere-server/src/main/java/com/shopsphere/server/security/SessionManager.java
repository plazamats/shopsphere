package com.shopsphere.server.security;

import com.shopsphere.common.protocol.Role;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Keeps the logged in sessions in memory. A token is long and random. A token only works on the
 * connection that logged in, and expires after a period of inactivity.
 */
public class SessionManager {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final long idleMillis;
    private final LongSupplier clock;

    public SessionManager(int idleMinutes) {
        this(idleMinutes, System::currentTimeMillis);
    }

    /** The clock can be replaced in tests. */
    public SessionManager(int idleMinutes, LongSupplier clock) {
        this.idleMillis = idleMinutes * 60_000L;
        this.clock = clock;
    }

    public Session create(long userId, String email, Role role, long connectionId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Session s = new Session(token, userId, email, role, connectionId, clock.getAsLong());
        sessions.put(token, s);
        return s;
    }

    /** The session for this token on this connection, if it is still valid. */
    public Optional<Session> resolve(String token, long connectionId) {
        if (token == null) return Optional.empty();
        Session s = sessions.get(token);
        if (s == null || s.connectionId() != connectionId) return Optional.empty();
        long now = clock.getAsLong();
        if (now - s.lastAccessMillis() > idleMillis) {
            sessions.remove(token);
            return Optional.empty();
        }
        s.touch(now);
        return Optional.of(s);
    }

    public void invalidate(String token) {
        if (token != null) sessions.remove(token);
    }

    /** Used when an account is suspended or its role changes: every session of the user ends. */
    public void invalidateUser(long userId) {
        sessions.values().removeIf(s -> s.userId() == userId);
    }

    /** Used after a password change: other sessions of the user end, the current one stays. */
    public void invalidateUserExcept(long userId, String keepToken) {
        sessions.values().removeIf(s -> s.userId() == userId && !s.token().equals(keepToken));
    }

    /** Used when a client disconnects. */
    public void invalidateConnection(long connectionId) {
        sessions.values().removeIf(s -> s.connectionId() == connectionId);
    }

    public int activeCount() {
        return sessions.size();
    }
}
