package com.meditrack.repository;

import com.meditrack.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Integer> {
    Optional<Doctor> findByUser_UserId(Integer userId);
    Optional<Doctor> findByDoctorCode(String doctorCode);
    boolean existsByDoctorCode(String doctorCode);
}
