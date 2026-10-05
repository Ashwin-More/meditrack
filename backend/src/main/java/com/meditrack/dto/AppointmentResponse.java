package com.meditrack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentResponse {
    private Integer appointmentId;
    private Integer patientId;
    private String patientName;
    private String patientCode;
    private Integer doctorId;
    private String doctorName;
    private LocalDateTime scheduledAt;
    private String reason;
    private String notes;
    private String status;
}
