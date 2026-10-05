package com.meditrack.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequest {

    /** Username, email address, or phone number — whichever the OTP was requested for. */
    @NotBlank(message = "Enter your username, email, or phone number")
    private String identifier;

    @NotBlank(message = "Enter the 6-digit code")
    @Pattern(regexp = "^\\d{6}$", message = "The code must be 6 digits")
    private String otp;

    @NotBlank(message = "New password is required")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
             message = "Password must be at least 8 characters and contain a letter and a number")
    private String newPassword;
}
