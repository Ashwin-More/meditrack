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
public class MedicalTestResponse {
    private Integer testId;
    private Integer visitId;
    private Integer patientId;
    private Integer doctorId;
    private String testName;
    private String testType;
    private LocalDate testDate;
    private String result;
    private String normalRange;
    private String remarks;
    private String status;
    private List<TestReportResponse> reports;
}
