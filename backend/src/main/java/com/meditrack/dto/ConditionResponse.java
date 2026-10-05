package com.meditrack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConditionResponse {
    private Integer conditionId;
    private String recordType;
    private String conditionName;
    private LocalDate diagnosedDate;
    private String status;
    private String notes;
}
