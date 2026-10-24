package com.bhargava.doctorappointment.dto;

public class AuthResponse {
    private final String accessToken;
    private final String tokenType = "Bearer";
    private final long expiresInSeconds;
    private final String email;
    private final String role;

    public AuthResponse(String accessToken, long expiresInSeconds, String email, String role) {
        this.accessToken = accessToken;
        this.expiresInSeconds = expiresInSeconds;
        this.email = email;
        this.role = role;
    }

    public String getAccessToken() { return accessToken; }
    public String getTokenType() { return tokenType; }
    public long getExpiresInSeconds() { return expiresInSeconds; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
}
