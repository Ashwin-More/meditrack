package com.meditrack.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** Used by Admin to create a user directly (auto-activated, auto-creates linked profile). */
@Getter
@Setter
public class CreateUserRequest {

    @NotBlank private String username;
    @NotBlank @Email private String email;
    @NotBlank
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
             message = "Password must be at least 8 characters and contain a letter and a number")
    private String password;
    @NotBlank private String firstName;
    @NotBlank private String lastName;
    private String phone;

    @NotBlank
    @Pattern(regexp = "ADMIN|DOCTOR|NURSE")
    private String role;

    private String specialization;
    private String qualification;
    private String licenseNumber;
}
