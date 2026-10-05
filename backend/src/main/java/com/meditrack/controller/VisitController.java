package com.meditrack.controller;

import com.meditrack.dto.DiagnosisRequest;
import com.meditrack.dto.VisitRequest;
import com.meditrack.dto.DiagnosisResponse;
import com.meditrack.dto.VisitResponse;
import com.meditrack.service.VisitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/visits")
@RequiredArgsConstructor
public class VisitController {

    private final VisitService visitService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','DOCTOR')")
    public ResponseEntity<VisitResponse> create(@Valid @RequestBody VisitRequest request) {
        return ResponseEntity.ok(visitService.createVisit(request));
    }

    @GetMapping("/{visitId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<VisitResponse> get(@PathVariable Integer visitId) {
        return ResponseEntity.ok(visitService.getVisit(visitId));
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<List<VisitResponse>> getForPatient(@PathVariable Integer patientId) {
        return ResponseEntity.ok(visitService.getVisitsForPatient(patientId));
    }

    @PostMapping("/{visitId}/close")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','DOCTOR')")
    public ResponseEntity<VisitResponse> close(@PathVariable Integer visitId) {
        return ResponseEntity.ok(visitService.closeVisit(visitId));
    }

    @PostMapping("/{visitId}/diagnoses")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<DiagnosisResponse> addDiagnosis(@PathVariable Integer visitId, @Valid @RequestBody DiagnosisRequest request) {
        return ResponseEntity.ok(visitService.addDiagnosis(visitId, request));
    }
}
