package com.meditrack.controller;

import com.meditrack.dto.VitalRequest;
import com.meditrack.dto.VitalResponse;
import com.meditrack.service.VitalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/vitals")
@RequiredArgsConstructor
public class VitalController {

    private final VitalService vitalService;

    /** Nurses (care) and doctors (clinical) record vitals; admins can only view. */
    @PostMapping
    @PreAuthorize("hasAnyRole('DOCTOR','NURSE')")
    public ResponseEntity<VitalResponse> record(@Valid @RequestBody VitalRequest request) {
        return ResponseEntity.ok(vitalService.record(request));
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<List<VitalResponse>> forPatient(@PathVariable Integer patientId) {
        return ResponseEntity.ok(vitalService.listForPatient(patientId));
    }
}
