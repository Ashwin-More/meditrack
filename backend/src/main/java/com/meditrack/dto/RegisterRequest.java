package com.meditrack.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {

    /**
     * Chosen by the doctor/nurse and stored exactly as entered. Must start with a letter so it can
     * never look like a phone number or email (login accepts username, email or phone).
     */
    @NotBlank(message = "Username is required")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9._-]{2,49}$",
             message = "Username must be 3-50 characters, start with a letter, and use only letters, numbers, dot, underscore or hyphen")
    private String username;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
             message = "Password must be at least 8 characters and contain a letter and a number")
    private String password;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    /** Required now that phone doubles as a login identifier and OTP-delivery channel. */
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[+]?[0-9 ()-]{7,20}$", message = "Enter a valid phone number")
    private String phone;

    /** DOCTOR or NURSE — self-registration is not allowed for ADMIN */
    @NotBlank
    @Pattern(regexp = "DOCTOR|NURSE", message = "role must be DOCTOR or NURSE")
    private String role;

    // Doctor-specific (optional, only used when role=DOCTOR)
    private String specialization;
    private String qualification;
    private String licenseNumber;

    // Nurse-specific (optional, only used when role=NURSE)
    private String nurseQualification;
}
