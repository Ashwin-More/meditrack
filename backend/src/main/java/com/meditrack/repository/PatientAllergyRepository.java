package com.meditrack.repository;

import com.meditrack.entity.PatientAllergy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatientAllergyRepository extends JpaRepository<PatientAllergy, Integer> {
    List<PatientAllergy> findByPatient_PatientId(Integer patientId);
}
