package com.meditrack.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DiagnosisRequest {
    @NotBlank private String diagnosisName;
    private String description;
    private String severity;
    private String notes;
}
