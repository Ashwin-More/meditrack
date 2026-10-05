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
public class AuditLogResponse {
    private Integer logId;
    private String username;
    private String action;
    private String tableName;
    private String recordId;
    private String description;
    private String ipAddress;
    private LocalDateTime createdAt;
}
