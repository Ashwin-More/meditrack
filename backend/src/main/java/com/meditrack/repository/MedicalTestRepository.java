package com.meditrack.repository;

import com.meditrack.entity.MedicalTest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MedicalTestRepository extends JpaRepository<MedicalTest, Integer> {
    List<MedicalTest> findByVisit_VisitId(Integer visitId);
    List<MedicalTest> findByPatient_PatientIdOrderByTestDateDesc(Integer patientId);
}
