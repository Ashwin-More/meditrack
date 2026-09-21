package com.meditrack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrescriptionItemResponse {
    private Integer itemId;
    private String medicineName;
    private String dosage;
    private String frequency;
    private String duration;
    private String route;
    private String instructions;
}
