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

    @NotBlank
    @Size(min = 3, max = 50)
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
