package com.meditrack.controller;

import com.meditrack.dto.MedicalTestRequest;
import com.meditrack.dto.MedicalTestResponse;
import com.meditrack.service.MedicalTestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/medical-tests")
@RequiredArgsConstructor
public class MedicalTestController {

    private final MedicalTestService medicalTestService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','DOCTOR')")
    public ResponseEntity<MedicalTestResponse> order(@Valid @RequestBody MedicalTestRequest request) {
        return ResponseEntity.ok(medicalTestService.orderTest(request));
    }

    @PutMapping("/{testId}/result")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<MedicalTestResponse> updateResult(@PathVariable Integer testId, @Valid @RequestBody MedicalTestRequest request) {
        return ResponseEntity.ok(medicalTestService.updateResult(testId, request));
    }

    @GetMapping("/{testId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<MedicalTestResponse> get(@PathVariable Integer testId) {
        return ResponseEntity.ok(medicalTestService.getTest(testId));
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<List<MedicalTestResponse>> getForPatient(@PathVariable Integer patientId) {
        return ResponseEntity.ok(medicalTestService.getTestsForPatient(patientId));
    }
}
