package com.meditrack.service;

import com.meditrack.dto.AppointmentRequest;
import com.meditrack.dto.AppointmentResponse;
import com.meditrack.entity.Appointment;
import com.meditrack.entity.Doctor;
import com.meditrack.entity.Patient;
import com.meditrack.enums.AppointmentStatus;
import com.meditrack.enums.AuditAction;
import com.meditrack.exception.BadRequestException;
import com.meditrack.exception.NotAssignedException;
import com.meditrack.exception.ResourceNotFoundException;
import com.meditrack.repository.AppointmentRepository;
import com.meditrack.repository.DoctorRepository;
import com.meditrack.repository.UserRepository;
import com.meditrack.security.AccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final AccessControlService accessControl;

    @Override
    @Transactional
    public AppointmentResponse create(AppointmentRequest request) {
        Patient patient = accessControl.loadAccessiblePatient(request.getPatientId());

        // A doctor always books for themselves; an admin picks the doctor.
        Integer doctorId = accessControl.isDoctor() ? accessControl.currentDoctorId() : request.getDoctorId();
        if (doctorId == null) {
            throw new BadRequestException("doctorId is required");
        }
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + doctorId));

        if (request.getScheduledAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Appointment time must be in the future");
        }
        if (appointmentRepository.existsByDoctor_DoctorIdAndScheduledAtAndStatus(
                doctorId, request.getScheduledAt(), AppointmentStatus.SCHEDULED)) {
            throw new BadRequestException("This doctor already has an appointment at that time");
        }

        Appointment appointment = appointmentRepository.save(Appointment.builder()
                .patient(patient)
                .doctor(doctor)
                .scheduledAt(request.getScheduledAt())
                .reason(request.getReason())
                .notes(request.getNotes())
                .status(AppointmentStatus.SCHEDULED)
                .build());

        auditLogService.log(userRepository.findById(accessControl.currentPrincipal().getUserId()).orElse(null),
                AuditAction.CREATE, "appointments", String.valueOf(appointment.getAppointmentId()),
                "Appointment booked for " + patient.getPatientCode(), null);
        return toResponse(appointment);
    }

    @Override
    public List<AppointmentResponse> listMine() {
        List<Appointment> list;
        if (accessControl.isDoctor()) {
            Integer doctorId = accessControl.currentDoctorId();
            list = doctorId == null ? List.of() : appointmentRepository.findByDoctor_DoctorIdOrderByScheduledAtAsc(doctorId);
        } else if (accessControl.isNurse()) {
            Integer nurseId = accessControl.currentNurseId();
            list = nurseId == null ? List.of() : appointmentRepository.findByPatient_AssignedNurse_NurseIdOrderByScheduledAtAsc(nurseId);
        } else {
            list = appointmentRepository.findAllByOrderByScheduledAtAsc();
        }
        return list.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<AppointmentResponse> listForPatient(Integer patientId) {
        accessControl.loadAccessiblePatient(patientId);
        return appointmentRepository.findByPatient_PatientIdOrderByScheduledAtDesc(patientId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AppointmentResponse updateStatus(Integer appointmentId, String status) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + appointmentId));
        if (accessControl.isDoctor()) {
            Integer me = accessControl.currentDoctorId();
            if (me == null || !me.equals(appointment.getDoctor().getDoctorId())) {
                throw new NotAssignedException("You can only update your own appointments");
            }
        }
        appointment.setStatus(AppointmentStatus.valueOf(status));
        return toResponse(appointmentRepository.save(appointment));
    }

    private AppointmentResponse toResponse(Appointment a) {
        return AppointmentResponse.builder()
                .appointmentId(a.getAppointmentId())
                .patientId(a.getPatient().getPatientId())
                .patientName(a.getPatient().getFullName())
                .patientCode(a.getPatient().getPatientCode())
                .doctorId(a.getDoctor().getDoctorId())
                .doctorName(a.getDoctor().getFullName())
                .scheduledAt(a.getScheduledAt())
                .reason(a.getReason())
                .notes(a.getNotes())
                .status(a.getStatus().name())
                .build();
    }
}
