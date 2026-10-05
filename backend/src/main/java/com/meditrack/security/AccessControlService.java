package com.meditrack.security;

import com.meditrack.entity.Doctor;
import com.meditrack.entity.Nurse;
import com.meditrack.entity.Patient;
import com.meditrack.exception.NotAssignedException;
import com.meditrack.exception.ResourceNotFoundException;
import com.meditrack.repository.DoctorRepository;
import com.meditrack.repository.NurseRepository;
import com.meditrack.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Central place for "assigned patient" rules, on top of the role checks done by @PreAuthorize:
 *   SUPER_ADMIN - every patient (same as ADMIN, plus admin-account management elsewhere)
 *   ADMIN  - every patient
 *   DOCTOR - only patients whose assigned doctor is this doctor
 *   NURSE  - only patients whose assigned nurse is this nurse
 */
@Component
@RequiredArgsConstructor
public class AccessControlService {

    private final DoctorRepository doctorRepository;
    private final NurseRepository nurseRepository;
    private final PatientRepository patientRepository;

    public UserPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new AccessDeniedException("Not authenticated");
        }
        return principal;
    }

    public boolean isSuperAdmin() { return "SUPER_ADMIN".equals(currentPrincipal().getRole()); }
    public boolean isAdmin()  { return "ADMIN".equals(currentPrincipal().getRole()); }
    public boolean isDoctor() { return "DOCTOR".equals(currentPrincipal().getRole()); }
    public boolean isNurse()  { return "NURSE".equals(currentPrincipal().getRole()); }

    /** doctor_id of the logged-in doctor, or null if the caller is not a doctor. */
    public Integer currentDoctorId() {
        UserPrincipal p = currentPrincipal();
        if (!"DOCTOR".equals(p.getRole())) return null;
        return doctorRepository.findByUser_UserId(p.getUserId()).map(Doctor::getDoctorId).orElse(null);
    }

    /** nurse_id of the logged-in nurse, or null if the caller is not a nurse. */
    public Integer currentNurseId() {
        UserPrincipal p = currentPrincipal();
        if (!"NURSE".equals(p.getRole())) return null;
        return nurseRepository.findByUser_UserId(p.getUserId()).map(Nurse::getNurseId).orElse(null);
    }

    public void assertCanAccessPatient(Patient patient) {
        UserPrincipal p = currentPrincipal();
        switch (p.getRole()) {
            case "SUPER_ADMIN":
            case "ADMIN":
                return;
            case "DOCTOR": {
                Integer me = currentDoctorId();
                Doctor assigned = patient.getAssignedDoctor();
                if (me != null && assigned != null && me.equals(assigned.getDoctorId())) return;
                break;
            }
            case "NURSE": {
                Integer me = currentNurseId();
                Nurse assigned = patient.getAssignedNurse();
                if (me != null && assigned != null && me.equals(assigned.getNurseId())) return;
                break;
            }
            default:
                break;
        }
        throw new NotAssignedException("This patient is not assigned to you");
    }

    public Patient loadAccessiblePatient(Integer patientId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found: " + patientId));
        assertCanAccessPatient(patient);
        return patient;
    }

    /** A DOCTOR may only act as themselves; ADMIN/SUPER_ADMIN may act for anyone; NURSE may not use doctor-only actions. */
    public void assertActingAsDoctor(Integer requestedDoctorId) {
        if (isAdmin() || isSuperAdmin()) return;
        Integer me = currentDoctorId();
        if (me == null || !me.equals(requestedDoctorId)) {
            throw new NotAssignedException("Doctors can only perform this action as themselves");
        }
    }
}
