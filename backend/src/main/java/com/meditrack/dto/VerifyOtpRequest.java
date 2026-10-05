package com.meditrack.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifyOtpRequest {
    /** Username, email address, or phone number. */
    @NotBlank(message = "Enter your username, email, or phone number")
    private String identifier;

    @NotBlank(message = "Enter the 6-digit code")
    @Pattern(regexp = "^\\d{6}$", message = "The code must be 6 digits")
    private String otp;

    /** Only meaningful for login-otp/verify: "SUPER_ADMIN" or "STAFF". Optional, see LoginRequest. */
    @Pattern(regexp = "SUPER_ADMIN|STAFF", message = "portal must be SUPER_ADMIN or STAFF")
    private String portal;
}
