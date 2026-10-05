package com.meditrack.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Used by Admin to create a user directly (auto-activated, auto-creates linked profile). */
@Getter
@Setter
public class CreateUserRequest {

    /** Required for SUPER_ADMIN, DOCTOR and NURSE (stored as entered); generated (admin001...) for ADMIN. */
    @Size(max = 50)
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9._-]{2,49}$",
             message = "Username must be 3-50 characters, start with a letter, and use only letters, numbers, dot, underscore or hyphen")
    private String username;
    @NotBlank @Email private String email;
    @NotBlank
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
             message = "Password must be at least 8 characters and contain a letter and a number")
    private String password;
    @NotBlank private String firstName;
    @NotBlank private String lastName;
    private String phone;

    /** Service-layer enforces who may actually use SUPER_ADMIN/ADMIN — see UserServiceImpl.isAdminTier. */
    @NotBlank
    @Pattern(regexp = "SUPER_ADMIN|ADMIN|DOCTOR|NURSE")
    private String role;

    private String specialization;
    private String qualification;
    private String licenseNumber;
}
