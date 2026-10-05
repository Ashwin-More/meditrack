package com.meditrack.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** Used wherever only an identifier is needed: resend-otp, forgot-password, login-otp/request. */
@Getter
@Setter
public class OtpIdentifierRequest {
    /** Username, email, or phone number. */
    @NotBlank(message = "Enter your username, email, or phone number")
    private String identifier;

    /** Only meaningful for login-otp/request: "SUPER_ADMIN" or "STAFF". Optional, see LoginRequest. */
    @Pattern(regexp = "SUPER_ADMIN|STAFF", message = "portal must be SUPER_ADMIN or STAFF")
    private String portal;
}
