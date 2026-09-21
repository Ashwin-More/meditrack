package com.meditrack.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class VitalRequest {
    @NotNull(message = "patientId is required")
    private Integer patientId;

    @DecimalMin(value = "30.0", message = "Temperature must be 30–45 °C")
    @DecimalMax(value = "45.0", message = "Temperature must be 30–45 °C")
    private BigDecimal temperatureC;

    @Min(value = 20, message = "Pulse must be 20–250 bpm") @Max(value = 250, message = "Pulse must be 20–250 bpm")
    private Integer pulseBpm;

    @Min(value = 50, message = "Systolic BP must be 50–300") @Max(value = 300, message = "Systolic BP must be 50–300")
    private Integer systolicBp;

    @Min(value = 30, message = "Diastolic BP must be 30–200") @Max(value = 200, message = "Diastolic BP must be 30–200")
    private Integer diastolicBp;

    @Min(value = 5, message = "Respiratory rate must be 5–80") @Max(value = 80, message = "Respiratory rate must be 5–80")
    private Integer respiratoryRate;

    @Min(value = 50, message = "SpO2 must be 50–100 %") @Max(value = 100, message = "SpO2 must be 50–100 %")
    private Integer spo2;

    @DecimalMin(value = "1.0", message = "Weight must be 1–500 kg")
    @DecimalMax(value = "500.0", message = "Weight must be 1–500 kg")
    private BigDecimal weightKg;

    @Size(max = 500) private String notes;
}
