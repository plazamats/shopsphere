package com.shopsphere.server.model;

import com.shopsphere.common.dto.AuthDtos.UserDto;
import com.shopsphere.common.protocol.Role;
import com.shopsphere.common.protocol.UserStatus;
import java.time.Instant;

/** A user row including the password hash. This class never leaves the server. */
public record UserAccount(long id, String email, String firstName, String lastName, String phone,
                          String passwordHash, UserStatus status, int failedAttempts, Instant lockedUntil,
                          Role role, Instant createdAt, Instant lastLoginAt) {

    public UserDto toDto() {
        return new UserDto(id, email, firstName, lastName, phone, role, status, createdAt, lastLoginAt);
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    @Override
    public String toString() {
        return "UserAccount[id=" + id + ", email=" + email + ", role=" + role + "]";   // no hash
    }
}
