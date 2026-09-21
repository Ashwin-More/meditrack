package com.meditrack.repository;

import com.meditrack.entity.Vital;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VitalRepository extends JpaRepository<Vital, Integer> {
    List<Vital> findByPatient_PatientIdOrderByRecordedAtDesc(Integer patientId);
}
