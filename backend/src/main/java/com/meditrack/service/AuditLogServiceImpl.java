package com.meditrack.service;

import com.meditrack.dto.AuditLogResponse;
import com.meditrack.entity.AuditLog;
import com.meditrack.entity.User;
import com.meditrack.enums.AuditAction;
import com.meditrack.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional
    public void log(User actingUser, AuditAction action, String tableName, String recordId, String description, String ipAddress) {
        AuditLog log = AuditLog.builder()
                .user(actingUser)
                .action(action)
                .tableName(tableName)
                .recordId(recordId)
                .description(description)
                .ipAddress(ipAddress)
                .build();
        auditLogRepository.save(log);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toResponse);
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return AuditLogResponse.builder()
                .logId(log.getLogId())
                .username(log.getUser() != null ? log.getUser().getUsername() : "SYSTEM")
                .action(log.getAction() != null ? log.getAction().name() : null)
                .tableName(log.getTableName())
                .recordId(log.getRecordId())
                .description(log.getDescription())
                .ipAddress(log.getIpAddress())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
