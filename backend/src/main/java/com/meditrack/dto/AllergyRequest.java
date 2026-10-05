package com.meditrack.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AllergyRequest {
    @NotBlank private String allergyName;
    private String reaction;
    private String severity; // MILD / MODERATE / SEVERE
    private String notes;
}
