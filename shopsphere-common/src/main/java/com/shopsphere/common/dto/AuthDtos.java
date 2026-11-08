package com.shopsphere.common.dto;

import com.shopsphere.common.protocol.Role;
import com.shopsphere.common.protocol.UserStatus;
import java.io.Serializable;
import java.time.Instant;

/** DTOs for accounts, profiles and addresses. Passwords never appear in toString. */
public final class AuthDtos {

    private AuthDtos() { }

    public record RegisterRequest(String email, String password, String firstName, String lastName, String phone)
            implements Serializable {
        @Override public String toString() { return "RegisterRequest[email=" + email + "]"; }
    }

    public record LoginRequest(String email, String password) implements Serializable {
        @Override public String toString() { return "LoginRequest[email=" + email + "]"; }
    }

    public record LoginResult(String token, UserDto user) implements Serializable {
        @Override public String toString() { return "LoginResult[user=" + user + "]"; }
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) implements Serializable {
        @Override public String toString() { return "ChangePasswordRequest[hidden]"; }
    }

    public record UpdateProfileRequest(String firstName, String lastName, String phone) implements Serializable { }

    /** A user as the client sees it. There is no password hash here. */
    public record UserDto(long id, String email, String firstName, String lastName, String phone,
                          Role role, UserStatus status, Instant createdAt, Instant lastLoginAt)
            implements Serializable {
        public String fullName() { return firstName + " " + lastName; }
    }

    public record AddressDto(Long id, String recipient, String line1, String line2, String city,
                             String province, String postalCode, String country, String phone)
            implements Serializable {
        public String oneLine() {
            StringBuilder sb = new StringBuilder(recipient).append(", ").append(line1);
            if (line2 != null && !line2.isBlank()) sb.append(", ").append(line2);
            return sb.append(", ").append(city).append(", ").append(province).append(", ")
                    .append(postalCode).append(", ").append(country).toString();
        }
    }

    // administration of accounts
    public record UserStatusRequest(long userId, UserStatus status) implements Serializable { }

    public record RoleChangeRequest(long userId, Role role) implements Serializable { }

    public record CreateAdminRequest(String email, String password, String firstName, String lastName)
            implements Serializable {
        @Override public String toString() { return "CreateAdminRequest[email=" + email + "]"; }
    }
}
