package com.meditrack.repository;

import com.meditrack.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;

import com.meditrack.enums.AppointmentStatus;

import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Integer> {
    List<Appointment> findAllByOrderByScheduledAtAsc();
    List<Appointment> findByDoctor_DoctorIdOrderByScheduledAtAsc(Integer doctorId);
    List<Appointment> findByPatient_AssignedNurse_NurseIdOrderByScheduledAtAsc(Integer nurseId);
    List<Appointment> findByPatient_PatientIdOrderByScheduledAtDesc(Integer patientId);
    boolean existsByDoctor_DoctorIdAndScheduledAtAndStatus(Integer doctorId, LocalDateTime scheduledAt, AppointmentStatus status);
}
