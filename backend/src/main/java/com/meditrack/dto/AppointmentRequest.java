package com.meditrack.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class AppointmentRequest {
    @NotNull(message = "patientId is required")
    private Integer patientId;

    /** Ignored for DOCTOR callers (they always book for themselves); required for ADMIN. */
    private Integer doctorId;

    @NotNull(message = "scheduledAt is required")
    private LocalDateTime scheduledAt;

    @Size(max = 255) private String reason;
    @Size(max = 500) private String notes;
}
