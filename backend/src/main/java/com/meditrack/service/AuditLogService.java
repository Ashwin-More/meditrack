package com.meditrack.service;

import com.meditrack.dto.AuditLogResponse;
import com.meditrack.entity.User;
import com.meditrack.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuditLogService {
    void log(User actingUser, AuditAction action, String tableName, String recordId, String description, String ipAddress);
    Page<AuditLogResponse> getLogs(Pageable pageable);
}
