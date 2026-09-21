package com.meditrack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NurseResponse {
    private Integer nurseId;
    private String nurseCode;
    private String fullName;
    private String qualification;
    private String phone;
    private String status;
}
