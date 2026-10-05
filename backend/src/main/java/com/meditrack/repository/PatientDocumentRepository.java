package com.meditrack.repository;

import com.meditrack.entity.PatientDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatientDocumentRepository extends JpaRepository<PatientDocument, Integer> {
    List<PatientDocument> findByPatient_PatientIdOrderByUploadedAtDesc(Integer patientId);
}
