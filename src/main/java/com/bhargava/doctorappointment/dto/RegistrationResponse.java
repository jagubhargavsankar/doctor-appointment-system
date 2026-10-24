package com.bhargava.doctorappointment.dto;

public class RegistrationResponse {
    private final String message;
    private final String email;
    private final String role;

    public RegistrationResponse(String message, String email, String role) {
        this.message = message;
        this.email = email;
        this.role = role;
    }

    public String getMessage() { return message; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
}
