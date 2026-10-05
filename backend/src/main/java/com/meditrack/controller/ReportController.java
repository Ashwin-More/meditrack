package com.meditrack.controller;

import com.meditrack.dto.TestReportResponse;
import com.meditrack.security.UserPrincipal;
import com.meditrack.service.MedicalTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final MedicalTestService medicalTestService;

    @PostMapping(value = "/tests/{testId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<TestReportResponse> upload(@PathVariable Integer testId,
                                                       @RequestParam("file") MultipartFile file,
                                                       @RequestParam(required = false) String description,
                                                       Authentication authentication) {
        Integer uploadedByUserId = ((UserPrincipal) authentication.getPrincipal()).getUserId();
        return ResponseEntity.ok(medicalTestService.uploadReport(testId, file, description, uploadedByUserId));
    }

    @GetMapping("/{reportId}/download")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<Resource> download(@PathVariable Integer reportId, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        medicalTestService.assertDownloadAllowed(reportId, principal.getUserId(), principal.getRole());

        Resource resource = medicalTestService.downloadReport(reportId);
        String fileName = medicalTestService.getReportFileName(reportId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
