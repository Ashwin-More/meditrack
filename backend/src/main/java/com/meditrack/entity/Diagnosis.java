package com.meditrack.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "diagnoses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Diagnosis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "diagnosis_id")
    private Integer diagnosisId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_id", nullable = false)
    private Visit visit;

    @Column(name = "diagnosis_name", length = 150, nullable = false)
    private String diagnosisName;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "diagnosed_date")
    private LocalDate diagnosedDate;

    @Column(length = 20)
    private String severity;

    @Column(length = 255)
    private String notes;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.diagnosedDate == null) this.diagnosedDate = LocalDate.now();
    }
}
