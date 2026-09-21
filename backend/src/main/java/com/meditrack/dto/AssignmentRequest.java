package com.meditrack.dto;

import lombok.Getter;
import lombok.Setter;

/** Either field may be null to clear that assignment. */
@Getter
@Setter
public class AssignmentRequest {
    private Integer doctorId;
    private Integer nurseId;
}
