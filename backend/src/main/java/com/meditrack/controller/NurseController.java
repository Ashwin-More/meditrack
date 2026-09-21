package com.meditrack.controller;

import com.meditrack.dto.NurseResponse;
import com.meditrack.repository.NurseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/nurses")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE')")
public class NurseController {

    private final NurseRepository nurseRepository;

    @GetMapping
    public ResponseEntity<List<NurseResponse>> getAll() {
        List<NurseResponse> nurses = nurseRepository.findAll().stream()
                .map(n -> NurseResponse.builder()
                        .nurseId(n.getNurseId())
                        .nurseCode(n.getNurseCode())
                        .fullName(n.getFullName())
                        .qualification(n.getQualification())
                        .phone(n.getPhone())
                        .status(n.getStatus().name())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(nurses);
    }
}
