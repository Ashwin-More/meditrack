package com.meditrack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientResponse {
    private Integer patientId;
    private String patientCode;
    private String firstName;
    private String lastName;
    private String fullName;
    private LocalDate dateOfBirth;
    private Integer age;
    private String gender;
    private String bloodGroup;
    private String phone;
    private String email;
    private String address;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String status;
    private Integer assignedDoctorId;
    private String assignedDoctorName;
    private Integer assignedNurseId;
    private String assignedNurseName;
    private LocalDateTime createdAt;
    private List<AllergyResponse> allergies;
    private List<ConditionResponse> conditions;
}
