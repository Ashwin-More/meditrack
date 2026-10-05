package com.meditrack.controller;

import com.meditrack.dto.PrescriptionRequest;
import com.meditrack.dto.PrescriptionResponse;
import com.meditrack.service.PrescriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/prescriptions")
@RequiredArgsConstructor
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<PrescriptionResponse> create(@Valid @RequestBody PrescriptionRequest request) {
        return ResponseEntity.ok(prescriptionService.createPrescription(request));
    }

    @GetMapping("/{prescriptionId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<PrescriptionResponse> get(@PathVariable Integer prescriptionId) {
        return ResponseEntity.ok(prescriptionService.getPrescription(prescriptionId));
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<List<PrescriptionResponse>> getForPatient(@PathVariable Integer patientId) {
        return ResponseEntity.ok(prescriptionService.getPrescriptionsForPatient(patientId));
    }
}
