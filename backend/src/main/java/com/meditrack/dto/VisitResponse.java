package com.meditrack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitResponse {
    private Integer visitId;
    private Integer patientId;
    private String patientName;
    private Integer doctorId;
    private String doctorName;
    private LocalDateTime visitDate;
    private String visitType;
    private String chiefComplaint;
    private String symptoms;
    private String doctorNotes;
    private LocalDate followUpDate;
    private String status;
    private List<DiagnosisResponse> diagnoses;
}
