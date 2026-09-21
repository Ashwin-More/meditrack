package com.meditrack.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PrescriptionItemRequest {
    @NotBlank private String medicineName;
    private String dosage;
    private String frequency;
    private String duration;
    private String route;
    private String instructions;
}
