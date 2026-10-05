package com.meditrack.controller;

import com.meditrack.dto.PatientDocumentResponse;
import com.meditrack.security.UserPrincipal;
import com.meditrack.service.PatientDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * "Medical Documents / Attachments" — previous hospital records, discharge
 * summaries, insurance paperwork, etc. Kept separate from /medical-tests
 * and /reports, which are always tied to an ordered test.
 */
@RestController
@RequiredArgsConstructor
public class PatientDocumentController {

    private final PatientDocumentService documentService;

    @PostMapping(value = "/patients/{patientId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<PatientDocumentResponse> upload(@PathVariable Integer patientId,
                                                            @RequestParam(value = "documentName", required = false) String documentName,
                                                            @RequestParam(value = "documentType", required = false) String documentType,
                                                            @RequestParam("file") MultipartFile file,
                                                            @RequestParam(value = "description", required = false) String description,
                                                            Authentication authentication) {
        Integer uploadedByUserId = ((UserPrincipal) authentication.getPrincipal()).getUserId();
        return ResponseEntity.ok(documentService.upload(patientId, documentName, documentType, file, description, uploadedByUserId));
    }

    @GetMapping("/patients/{patientId}/documents")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<List<PatientDocumentResponse>> list(@PathVariable Integer patientId) {
        return ResponseEntity.ok(documentService.listForPatient(patientId));
    }

    @GetMapping("/patient-documents/{documentId}/download")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<Resource> download(@PathVariable Integer documentId, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        documentService.assertDownloadAllowed(documentId, principal.getUserId(), principal.getRole());

        // Same authorization check as before; the file is only prepared after it passes.
        PatientDocumentService.PreparedDownload file = documentService.prepareDownload(documentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.resource());
    }

    /**
     * Same access checks as /download, but streamed with Content-Disposition: inline so the
     * browser/website can display it. Only PDFs and images are shown inline; anything else
     * falls back to a normal attachment.
     */
    @GetMapping("/patient-documents/{documentId}/view")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<Resource> view(@PathVariable Integer documentId, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        documentService.assertDownloadAllowed(documentId, principal.getUserId(), principal.getRole());

        Resource resource = documentService.download(documentId);
        String fileName = documentService.getFileName(documentId);
        MediaType type = MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM);
        boolean inlineable = "image".equals(type.getType()) || MediaType.APPLICATION_PDF.equals(type);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, (inlineable ? "inline" : "attachment") + "; filename=\"" + fileName + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(inlineable ? type : MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
