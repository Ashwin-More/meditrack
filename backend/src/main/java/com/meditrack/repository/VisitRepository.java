package com.meditrack.repository;

import com.meditrack.entity.Visit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VisitRepository extends JpaRepository<Visit, Integer> {
    List<Visit> findByPatient_PatientIdOrderByVisitDateDesc(Integer patientId);
    Page<Visit> findByDoctor_DoctorId(Integer doctorId, Pageable pageable);
    Page<Visit> findByPatient_PatientId(Integer patientId, Pageable pageable);
}
