package com.meditrack.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    /** Username, email address, or phone number. */
    @NotBlank(message = "Username, email, or phone is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    /**
     * Which login portal the person picked on the frontend: "SUPER_ADMIN" or "STAFF"
     * (Admin/Doctor/Nurse). Optional — when omitted, no portal check is applied
     * (keeps older API callers working). When present, the resolved account's
     * actual role must match, or the login is rejected with a "wrong portal" message.
     */
    @Pattern(regexp = "SUPER_ADMIN|STAFF", message = "portal must be SUPER_ADMIN or STAFF")
    private String portal;
}
