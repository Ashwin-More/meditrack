package com.meditrack.repository;

import com.meditrack.entity.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Integer> {
    Optional<Patient> findByPatientCode(String patientCode);
    boolean existsByPatientCode(String patientCode);

    /** Admin: every patient matching the search text ('' matches all). */
    @Query("SELECT p FROM Patient p WHERE (" +
           "LOWER(p.firstName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(p.lastName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(p.patientCode) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "p.phone LIKE CONCAT('%', :q, '%'))")
    Page<Patient> search(@Param("q") String query, Pageable pageable);

    /** Doctor: only patients assigned to this doctor. */
    @Query("SELECT p FROM Patient p WHERE p.assignedDoctor.doctorId = :doctorId AND (" +
           "LOWER(p.firstName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(p.lastName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(p.patientCode) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "p.phone LIKE CONCAT('%', :q, '%'))")
    Page<Patient> searchForDoctor(@Param("q") String query, @Param("doctorId") Integer doctorId, Pageable pageable);

    /** Nurse: only patients assigned to this nurse. */
    @Query("SELECT p FROM Patient p WHERE p.assignedNurse.nurseId = :nurseId AND (" +
           "LOWER(p.firstName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(p.lastName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(p.patientCode) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "p.phone LIKE CONCAT('%', :q, '%'))")
    Page<Patient> searchForNurse(@Param("q") String query, @Param("nurseId") Integer nurseId, Pageable pageable);

    long countByPatientCodeStartingWith(String prefix);
}
