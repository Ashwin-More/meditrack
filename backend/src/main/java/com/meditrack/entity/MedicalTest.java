package com.meditrack.entity;

import com.meditrack.enums.TestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "medical_tests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "test_id")
    private Integer testId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_id", nullable = false)
    private Visit visit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "test_name", length = 150, nullable = false)
    private String testName;

    @Column(name = "test_type", length = 50)
    private String testType; // BLOOD / X-RAY / MRI / CT / URINE ...

    @Column(name = "test_date")
    private LocalDate testDate;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String result;

    @Column(name = "normal_range", length = 100)
    private String normalRange;

    @Column(length = 255)
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(length = 20)
    private TestStatus status = TestStatus.PENDING;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder.Default
    @OneToMany(mappedBy = "test", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TestReport> reports = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.testDate == null) this.testDate = LocalDate.now();
    }
}
