package com.meditrack.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class VisitRequest {

    @NotNull private Integer patientId;
    @NotNull private Integer doctorId;

    private String visitType; // OPD / FOLLOW_UP / EMERGENCY
    private String chiefComplaint;
    private String symptoms;
    private String doctorNotes;
    private LocalDate followUpDate;
}
