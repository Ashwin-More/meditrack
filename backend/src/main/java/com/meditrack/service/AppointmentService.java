package com.meditrack.service;

import com.meditrack.dto.AppointmentRequest;
import com.meditrack.dto.AppointmentResponse;

import java.util.List;

public interface AppointmentService {
    AppointmentResponse create(AppointmentRequest request);
    /** ADMIN: all appointments; DOCTOR: own; NURSE: those of patients assigned to the nurse. */
    List<AppointmentResponse> listMine();
    List<AppointmentResponse> listForPatient(Integer patientId);
    AppointmentResponse updateStatus(Integer appointmentId, String status);
}
