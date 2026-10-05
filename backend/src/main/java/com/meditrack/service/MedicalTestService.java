package com.meditrack.service;

import com.meditrack.dto.MedicalTestRequest;
import com.meditrack.dto.MedicalTestResponse;
import com.meditrack.dto.TestReportResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MedicalTestService {
    MedicalTestResponse orderTest(MedicalTestRequest request);
    MedicalTestResponse updateResult(Integer testId, MedicalTestRequest request);
    MedicalTestResponse getTest(Integer testId);
    List<MedicalTestResponse> getTestsForPatient(Integer patientId);
    TestReportResponse uploadReport(Integer testId, MultipartFile file, String description, Integer uploadedByUserId);
    org.springframework.core.io.Resource downloadReport(Integer reportId);
    String getReportFileName(Integer reportId);

    /**
     * Enforces report-download visibility rules:
     * The caller must first have access to the patient (assigned doctor/nurse, or ADMIN).
     * ADMIN and DOCTOR can then download any report of that patient. A NURSE can only download a
     * report they uploaded themselves, or one uploaded by a DOCTOR — not a
     * report uploaded by another nurse.
     * Throws org.springframework.security.access.AccessDeniedException if not allowed.
     */
    void assertDownloadAllowed(Integer reportId, Integer requestingUserId, String requestingUserRole);
}
