package com.meditrack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllergyResponse {
    private Integer allergyId;
    private String allergyName;
    private String reaction;
    private String severity;
    private String notes;
}
