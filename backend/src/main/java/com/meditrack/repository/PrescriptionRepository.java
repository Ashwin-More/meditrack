package com.meditrack.repository;

import com.meditrack.entity.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrescriptionRepository extends JpaRepository<Prescription, Integer> {
    List<Prescription> findByVisit_VisitId(Integer visitId);
    List<Prescription> findByPatient_PatientIdOrderByPrescriptionDateDesc(Integer patientId);
}
