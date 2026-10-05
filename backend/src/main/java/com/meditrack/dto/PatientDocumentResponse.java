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
public class PatientDocumentResponse {
    private Integer documentId;
    private Integer patientId;
    private String documentName;
    private String documentType;
    private String fileName;
    private String fileType;
    private Integer fileSize;
    private String description;
    private String uploadedByName;
    private String uploadedByRole;
    private LocalDateTime uploadedAt;
    private String downloadUrl;
    /** PatientName_DocumentType.pdf - what the browser should save the download as. */
    private String downloadFileName;
}
