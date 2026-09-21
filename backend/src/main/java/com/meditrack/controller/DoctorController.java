package com.meditrack.controller;

import com.meditrack.dto.DoctorResponse;
import com.meditrack.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/doctors")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE')")
public class DoctorController {

    private final DoctorRepository doctorRepository;

    @GetMapping
    public ResponseEntity<List<DoctorResponse>> getAll() {
        List<DoctorResponse> doctors = doctorRepository.findAll().stream()
                .map(d -> DoctorResponse.builder()
                        .doctorId(d.getDoctorId())
                        .doctorCode(d.getDoctorCode())
                        .fullName(d.getFullName())
                        .specialization(d.getSpecialization())
                        .qualification(d.getQualification())
                        .licenseNumber(d.getLicenseNumber())
                        .phone(d.getPhone())
                        .status(d.getStatus().name())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(doctors);
    }
}
