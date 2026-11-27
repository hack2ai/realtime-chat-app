package com.chatapp.model.dto;

/**
 * Payload DTOs for the authentication message flow.
 *
 * <p>Kept as small static nested classes in one file (rather than one
 * file per DTO) since they're tightly related and trivially small —
 * splitting them out would mean six near-empty files for six fields
 * total. This is a deliberate exception to "one public class per file";
 * all classes here are package-visible-from-outside via the enclosing
 * public class but have no behavior of their own.
 */
public final class AuthDTOs {

    private AuthDTOs() {
    }

    /** Payload for {@code C2S_REGISTER}. */
    public static class RegisterRequest {
        private String username;
        private String email;
        private String password;        // plaintext, sent once over the wire, never stored
        private String confirmPassword;

        public RegisterRequest() {
        }

        public RegisterRequest(String username, String email, String password, String confirmPassword) {
            this.username = username;
            this.email = email;
            this.password = password;
            this.confirmPassword = confirmPassword;
        }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getConfirmPassword() { return confirmPassword; }
        public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
    }

    /** Payload for {@code S2C_REGISTER_SUCCESS}. */
    public static class RegisterSuccessResponse {
        private int userId;
        private String username;

        public RegisterSuccessResponse() {
        }

        public RegisterSuccessResponse(int userId, String username) {
            this.userId = userId;
            this.username = username;
        }

        public int getUserId() { return userId; }
        public void setUserId(int userId) { this.userId = userId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
    }

    /** Payload for {@code S2C_REGISTER_FAILED} and {@code S2C_LOGIN_FAILED}. */
    public static class AuthFailedResponse {
        private String reason;

        public AuthFailedResponse() {
        }

        public AuthFailedResponse(String reason) {
            this.reason = reason;
        }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    /**
     * Payload for {@code C2S_LOGIN}.
     * {@code usernameOrEmail} accepts either, matching the spec's
     * "Username/Email" login field requirement.
     */
    public static class LoginRequest {
        private String usernameOrEmail;
        private String password;

        public LoginRequest() {
        }

        public LoginRequest(String usernameOrEmail, String password) {
            this.usernameOrEmail = usernameOrEmail;
            this.password = password;
        }

        public String getUsernameOrEmail() { return usernameOrEmail; }
        public void setUsernameOrEmail(String usernameOrEmail) { this.usernameOrEmail = usernameOrEmail; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    /**
     * Payload for {@code S2C_LOGIN_SUCCESS}.
     * Carries a session token the client must echo back (e.g. as part
     * of the connection's authenticated state) — see
     * {@code AuthenticationService} for how the token is generated,
     * stored, and validated.
     */
    public static class LoginSuccessResponse {
        private int userId;
        private String username;
        private String role;
        private String sessionToken;

        public LoginSuccessResponse() {
        }

        public LoginSuccessResponse(int userId, String username, String role, String sessionToken) {
            this.userId = userId;
            this.username = username;
            this.role = role;
            this.sessionToken = sessionToken;
        }

        public int getUserId() { return userId; }
        public void setUserId(int userId) { this.userId = userId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getSessionToken() { return sessionToken; }
        public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }
    }
}
