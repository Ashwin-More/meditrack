package com.meditrack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisResponse {
    private Integer diagnosisId;
    private String diagnosisName;
    private String description;
    private LocalDate diagnosedDate;
    private String severity;
    private String notes;
}
