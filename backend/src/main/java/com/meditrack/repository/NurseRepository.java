package com.meditrack.repository;

import com.meditrack.entity.Nurse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NurseRepository extends JpaRepository<Nurse, Integer> {
    Optional<Nurse> findByUser_UserId(Integer userId);
    Optional<Nurse> findByNurseCode(String nurseCode);
    boolean existsByNurseCode(String nurseCode);
}
