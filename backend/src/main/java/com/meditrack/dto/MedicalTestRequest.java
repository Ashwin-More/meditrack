package com.meditrack.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MedicalTestRequest {

    @NotNull private Integer visitId;
    @NotNull private Integer patientId;
    @NotNull private Integer doctorId;

    @NotBlank private String testName;
    private String testType;
    private String result;
    private String normalRange;
    private String remarks;
    private String status; // PENDING / COMPLETED
}
