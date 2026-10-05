package com.meditrack.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ConditionRequest {
    @NotBlank private String conditionName;
    private LocalDate diagnosedDate;
    private String status; // ONGOING / CHRONIC / RESOLVED
    private String recordType; // CONDITION / SURGERY / HOSPITALIZATION / NOTE (default CONDITION)
    private String notes;
}
