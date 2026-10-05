package com.meditrack.repository;

import com.meditrack.entity.PatientCondition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatientConditionRepository extends JpaRepository<PatientCondition, Integer> {
    List<PatientCondition> findByPatient_PatientId(Integer patientId);
}
