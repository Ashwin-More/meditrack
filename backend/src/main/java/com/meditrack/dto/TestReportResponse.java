package com.meditrack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestReportResponse {
    private Integer reportId;
    private String fileName;
    private String fileType;
    private Integer fileSize;
    private String uploadedByName;
    private LocalDateTime uploadedAt;
    private String description;
    private String downloadUrl;
}
