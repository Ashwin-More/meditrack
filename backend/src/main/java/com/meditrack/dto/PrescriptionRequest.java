package com.meditrack.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PrescriptionRequest {

    @NotNull private Integer visitId;
    @NotNull private Integer doctorId;
    @NotNull private Integer patientId;

    private String instructions;

    @NotEmpty(message = "At least one medicine item is required")
    @Valid
    private List<PrescriptionItemRequest> items;
}
