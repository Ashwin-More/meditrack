package com.meditrack.entity;

import com.meditrack.enums.ActiveStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "nurses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Nurse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "nurse_id")
    private Integer nurseId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    @Column(name = "nurse_code", length = 20, unique = true, nullable = false)
    private String nurseCode;

    @Column(length = 150)
    private String qualification;

    @Column(length = 20)
    private String phone;

    @Column(name = "joining_date")
    private LocalDate joiningDate;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(length = 20)
    private ActiveStatus status = ActiveStatus.ACTIVE;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Transient
    public String getFullName() {
        return user != null ? user.getFullName() : nurseCode;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
