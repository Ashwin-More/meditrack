package com.meditrack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrescriptionResponse {
    private Integer prescriptionId;
    private Integer visitId;
    private Integer doctorId;
    private String doctorName;
    private Integer patientId;
    private String patientName;
    private LocalDateTime prescriptionDate;
    private String instructions;
    private List<PrescriptionItemResponse> items;
    /** Allergy names that overlap with prescribed medicines — shown as a warning banner in the UI. */
    private List<String> allergyWarnings;
}
