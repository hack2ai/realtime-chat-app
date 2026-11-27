package com.chatapp.model;

import java.time.LocalDateTime;

/**
 * Domain model for a registered user, mapping to the {@code users} table.
 *
 * <p>Deliberately does NOT implement {@link java.io.Serializable} — this
 * project's wire protocol is JSON (via Gson), not Java object
 * serialization, so model classes only need to be plain POJOs that Gson
 * can reflectively (de)serialize. See {@code socket/protocol} package
 * for the actual wire-format envelope.
 *
 * <p>The password hash is intentionally named {@code passwordHash} (never
 * "password") throughout this codebase as a constant reminder that
 * plaintext passwords must never be stored, logged, or held in this
 * field. See {@link com.chatapp.service.AuthenticationService}.
 */
public class User {

    public enum Role { USER, ADMIN }
    public enum Status { ONLINE, OFFLINE, AWAY }

    private int id;
    private String username;
    private String email;

    /** BCrypt hash only. Never populate this with a plaintext password. */
    private String passwordHash;

    private String profileImage;
    private Role role;
    private Status status;
    private LocalDateTime lastSeen;
    private LocalDateTime createdAt;

    public User() {
        // Required no-arg constructor for Gson and JDBC row mapping.
    }

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = Role.USER;
        this.status = Status.OFFLINE;
    }

    // ---------------------------------------------------------------
    // Getters / setters
    // ---------------------------------------------------------------

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getProfileImage() { return profileImage; }
    public void setProfileImage(String profileImage) { this.profileImage = profileImage; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public LocalDateTime getLastSeen() { return lastSeen; }
    public void setLastSeen(LocalDateTime lastSeen) { this.lastSeen = lastSeen; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /**
     * Returns a copy of this user safe to send over the wire to other
     * clients (e.g. for "user list" or "user came online" broadcasts).
     * Strips the password hash so it is never serialized into a JSON
     * packet, even by accident.
     */
    public User toPublicView() {
        User publicUser = new User();
        publicUser.id = this.id;
        publicUser.username = this.username;
        publicUser.profileImage = this.profileImage;
        publicUser.status = this.status;
        publicUser.lastSeen = this.lastSeen;
        // email, passwordHash, role, createdAt deliberately omitted.
        return publicUser;
    }

    @Override
    public String toString() {
        return "User{id=%d, username='%s', status=%s}".formatted(id, username, status);
    }
}
