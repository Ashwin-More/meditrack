package com.meditrack.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "prescription_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrescriptionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Integer itemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescription_id", nullable = false)
    private Prescription prescription;

    @Column(name = "medicine_name", length = 150, nullable = false)
    private String medicineName;

    @Column(length = 50)
    private String dosage;      // e.g. 500 mg

    @Column(length = 50)
    private String frequency;   // e.g. 2 times/day

    @Column(length = 50)
    private String duration;    // e.g. 5 days

    @Column(length = 30)
    private String route;       // e.g. Oral

    @Column(length = 255)
    private String instructions;
}
