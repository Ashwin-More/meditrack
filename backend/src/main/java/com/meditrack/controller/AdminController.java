package com.meditrack.controller;

import com.meditrack.dto.AssignmentRequest;
import com.meditrack.dto.CreateUserRequest;
import com.meditrack.dto.PatientResponse;
import com.meditrack.service.PatientService;
import com.meditrack.dto.AuditLogResponse;
import com.meditrack.dto.UserResponse;
import com.meditrack.service.AuditLogService;
import com.meditrack.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** All endpoints here require the ADMIN role. */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
public class AdminController {

    private final UserService userService;
    private final AuditLogService auditLogService;
    private final PatientService patientService;

    /**
     * Assign (or clear) the doctor and nurse responsible for a patient. Normally admin-only, but a
     * nurse now has the same power as an admin for this one action (option A) — she can assign any
     * patient in the hospital to any doctor/nurse, not just patients already in her own care.
     */
    @PutMapping("/patients/{patientId}/assignment")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','NURSE')")
    public ResponseEntity<PatientResponse> assignPatient(@PathVariable Integer patientId,
                                                         @RequestBody AssignmentRequest request) {
        return ResponseEntity.ok(patientService.assignPatient(patientId, request));
    }

    @PostMapping("/users")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(userService.createUser(request));
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/users/pending")
    public ResponseEntity<List<UserResponse>> getPendingUsers() {
        return ResponseEntity.ok(userService.getPendingUsers());
    }

    @PostMapping("/users/{userId}/approve")
    public ResponseEntity<UserResponse> approveUser(@PathVariable Integer userId) {
        return ResponseEntity.ok(userService.approveUser(userId));
    }

    @PostMapping("/users/{userId}/reject")
    public ResponseEntity<UserResponse> rejectUser(@PathVariable Integer userId) {
        return ResponseEntity.ok(userService.rejectUser(userId));
    }

    @PostMapping("/users/{userId}/disable")
    public ResponseEntity<UserResponse> disableUser(@PathVariable Integer userId) {
        return ResponseEntity.ok(userService.disableUser(userId));
    }

    @PostMapping("/users/{userId}/enable")
    public ResponseEntity<UserResponse> enableUser(@PathVariable Integer userId) {
        return ResponseEntity.ok(userService.enableUser(userId));
    }

    @PostMapping("/users/{userId}/resend-verification")
    public ResponseEntity<Void> resendVerification(@PathVariable Integer userId) {
        userService.resendVerification(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogs(Pageable pageable) {
        return ResponseEntity.ok(auditLogService.getLogs(pageable));
    }
}
