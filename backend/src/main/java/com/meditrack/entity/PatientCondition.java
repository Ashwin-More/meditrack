package com.meditrack.entity;

import com.meditrack.enums.ConditionStatus;
import com.meditrack.enums.RecordType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "patient_conditions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "condition_id")
    private Integer conditionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    /** CONDITION (default), SURGERY, HOSPITALIZATION or NOTE — all part of the patient's Medical History. */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "record_type", length = 20, nullable = false)
    private RecordType recordType = RecordType.CONDITION;

    @Column(name = "condition_name", length = 100, nullable = false)
    private String conditionName;

    @Column(name = "diagnosed_date")
    private LocalDate diagnosedDate;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(length = 20)
    private ConditionStatus status = ConditionStatus.ONGOING;

    @Column(length = 255)
    private String notes;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
