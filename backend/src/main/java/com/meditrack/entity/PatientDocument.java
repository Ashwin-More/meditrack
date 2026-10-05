package com.meditrack.entity;

import com.meditrack.enums.DocumentType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A "Medical Documents / Attachments" record: previous hospital records,
 * discharge summaries, insurance documents, etc. Distinct from
 * {@link TestReport}, which is always tied to a specific {@link MedicalTest}.
 * Only the file's metadata lives here — the file itself sits on disk (or
 * object storage) under app.upload.dir, never inline in the database.
 */
@Entity
@Table(name = "patient_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id")
    private Integer documentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "document_name", length = 150, nullable = false)
    private String documentName;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "document_type", length = 30, nullable = false)
    private DocumentType documentType = DocumentType.OTHER;

    @Column(name = "file_name", length = 255, nullable = false)
    private String fileName;

    @Column(name = "file_path", length = 500, nullable = false)
    private String filePath;

    @Column(name = "file_type", length = 10)
    private String fileType;

    @Column(name = "file_size")
    private Integer fileSize;

    @Column(length = 255)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        this.uploadedAt = LocalDateTime.now();
    }
}
