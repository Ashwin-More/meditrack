package com.meditrack.controller;

import com.meditrack.dto.AllergyRequest;
import com.meditrack.dto.ConditionRequest;
import com.meditrack.dto.PatientRequest;
import com.meditrack.dto.AllergyResponse;
import com.meditrack.dto.ConditionResponse;
import com.meditrack.dto.PatientResponse;
import com.meditrack.service.PatientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Accessible to ADMIN, DOCTOR and NURSE — patient records are shared clinical data. */
@RestController
@RequestMapping("/patients")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE')")
public class PatientController {

    private final PatientService patientService;

    @PostMapping
    public ResponseEntity<PatientResponse> create(@Valid @RequestBody PatientRequest request) {
        return ResponseEntity.ok(patientService.createPatient(request));
    }

    @PutMapping("/{patientId}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<PatientResponse> update(@PathVariable Integer patientId, @Valid @RequestBody PatientRequest request) {
        return ResponseEntity.ok(patientService.updatePatient(patientId, request));
    }

    @GetMapping("/{patientId}")
    public ResponseEntity<PatientResponse> get(@PathVariable Integer patientId) {
        return ResponseEntity.ok(patientService.getPatient(patientId));
    }

    @GetMapping
    public ResponseEntity<Page<PatientResponse>> search(@RequestParam(required = false) String q, Pageable pageable) {
        return ResponseEntity.ok(patientService.searchPatients(q, pageable));
    }

    @PostMapping("/{patientId}/allergies")
    public ResponseEntity<AllergyResponse> addAllergy(@PathVariable Integer patientId, @Valid @RequestBody AllergyRequest request) {
        return ResponseEntity.ok(patientService.addAllergy(patientId, request));
    }

    @PostMapping("/{patientId}/conditions")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<ConditionResponse> addCondition(@PathVariable Integer patientId, @Valid @RequestBody ConditionRequest request) {
        return ResponseEntity.ok(patientService.addCondition(patientId, request));
    }
}
