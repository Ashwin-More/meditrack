package com.meditrack.service;

import com.meditrack.dto.PrescriptionRequest;
import com.meditrack.dto.PrescriptionResponse;

import java.util.List;

public interface PrescriptionService {
    PrescriptionResponse createPrescription(PrescriptionRequest request);
    PrescriptionResponse getPrescription(Integer prescriptionId);
    List<PrescriptionResponse> getPrescriptionsForPatient(Integer patientId);
}
