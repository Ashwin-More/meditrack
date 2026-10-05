package com.meditrack.service;

import com.meditrack.dto.PatientDocumentResponse;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PatientDocumentService {
    PatientDocumentResponse upload(Integer patientId, String documentName, String documentType,
                                    MultipartFile file, String description, Integer uploadedByUserId);
    List<PatientDocumentResponse> listForPatient(Integer patientId);
    Resource download(Integer documentId);
    String getFileName(Integer documentId);

    /** A file ready to send for the Download button. */
    record PreparedDownload(Resource resource, String fileName, String contentType) {}

    /**
     * Download copy named PatientName_DocumentType.pdf. PDFs are sent as stored; images (JPG/PNG) are
     * converted to a PDF in memory - the stored original is never touched. Call {@link #assertDownloadAllowed} first.
     */
    PreparedDownload prepareDownload(Integer documentId);

    /**
     * Same visibility rule as test-report downloads: the caller must first be able to
     * access the patient (ADMIN, or the assigned DOCTOR/NURSE). A NURSE may then only
     * download a document they uploaded themselves, or one uploaded by a DOCTOR.
     */
    void assertDownloadAllowed(Integer documentId, Integer requestingUserId, String requestingUserRole);
}
