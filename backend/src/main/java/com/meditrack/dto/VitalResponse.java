package com.meditrack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VitalResponse {
    private Integer vitalId;
    private Integer patientId;
    private String recordedByName;
    private String recordedByRole;
    private BigDecimal temperatureC;
    private Integer pulseBpm;
    private Integer systolicBp;
    private Integer diastolicBp;
    private Integer respiratoryRate;
    private Integer spo2;
    private BigDecimal weightKg;
    private String notes;
    private LocalDateTime recordedAt;
}
