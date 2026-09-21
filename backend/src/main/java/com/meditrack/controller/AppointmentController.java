package com.meditrack.controller;

import com.meditrack.dto.AppointmentRequest;
import com.meditrack.dto.AppointmentResponse;
import com.meditrack.dto.AppointmentStatusRequest;
import com.meditrack.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    /** ADMIN / DOCTOR book appointments. Nurses have read-only access. */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<AppointmentResponse> create(@Valid @RequestBody AppointmentRequest request) {
        return ResponseEntity.ok(appointmentService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<List<AppointmentResponse>> listMine() {
        return ResponseEntity.ok(appointmentService.listMine());
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<List<AppointmentResponse>> forPatient(@PathVariable Integer patientId) {
        return ResponseEntity.ok(appointmentService.listForPatient(patientId));
    }

    @PutMapping("/{appointmentId}/status")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<AppointmentResponse> updateStatus(@PathVariable Integer appointmentId,
                                                            @Valid @RequestBody AppointmentStatusRequest request) {
        return ResponseEntity.ok(appointmentService.updateStatus(appointmentId, request.getStatus()));
    }
}
