package com.meditrack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorResponse {
    private Integer doctorId;
    private String doctorCode;
    private String fullName;
    private String specialization;
    private String qualification;
    private String licenseNumber;
    private String phone;
    private String status;
}
