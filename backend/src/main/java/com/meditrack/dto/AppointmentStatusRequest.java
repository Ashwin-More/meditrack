package com.meditrack.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AppointmentStatusRequest {
    @NotBlank
    @Pattern(regexp = "SCHEDULED|COMPLETED|CANCELLED", message = "status must be SCHEDULED, COMPLETED or CANCELLED")
    private String status;
}
