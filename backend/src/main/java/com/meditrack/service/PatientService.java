package com.meditrack.service;

import com.meditrack.dto.AllergyRequest;
import com.meditrack.dto.AssignmentRequest;
import com.meditrack.dto.ConditionRequest;
import com.meditrack.dto.PatientRequest;
import com.meditrack.dto.AllergyResponse;
import com.meditrack.dto.ConditionResponse;
import com.meditrack.dto.PatientResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PatientService {
    PatientResponse createPatient(PatientRequest request);
    PatientResponse updatePatient(Integer patientId, PatientRequest request);
    PatientResponse getPatient(Integer patientId);
    Page<PatientResponse> searchPatients(String query, Pageable pageable, boolean forAssignment);

    /**
     * Assign (or clear) both the doctor and the nurse for any patient — used by ADMIN/SUPER_ADMIN
     * and, since a nurse now has the same power as an admin for this one action, by NURSE too.
     */
    PatientResponse assignPatient(Integer patientId, AssignmentRequest request);

    AllergyResponse addAllergy(Integer patientId, AllergyRequest request);
    ConditionResponse addCondition(Integer patientId, ConditionRequest request);
}
