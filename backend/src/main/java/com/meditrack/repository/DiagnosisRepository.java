package com.meditrack.repository;

import com.meditrack.entity.Diagnosis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DiagnosisRepository extends JpaRepository<Diagnosis, Integer> {
    List<Diagnosis> findByVisit_VisitId(Integer visitId);
}
