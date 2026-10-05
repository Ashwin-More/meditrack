package com.meditrack.service;

import com.meditrack.dto.PatientDocumentResponse;
import com.meditrack.entity.Patient;
import com.meditrack.entity.PatientDocument;
import com.meditrack.entity.User;
import com.meditrack.enums.DocumentType;
import com.meditrack.exception.BadRequestException;
import com.meditrack.exception.ResourceNotFoundException;
import com.meditrack.repository.PatientDocumentRepository;
import com.meditrack.repository.UserRepository;
import com.meditrack.security.AccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import com.meditrack.util.FileNames;
import com.meditrack.util.ImageToPdf;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * "Medical Documents / Attachments" — free-form patient documents (previous
 * hospital records, discharge summaries, insurance paperwork, etc.), kept
 * separate from {@code medical_tests}/{@code test_reports} so a doctor can
 * see a patient's complete history — visits, prescriptions, test reports,
 * and now general documents — in one place. Only metadata lives in MySQL;
 * the file itself is stored on disk under {@code app.upload.dir}, in its
 * own sub-folder, and is only ever reachable through the authenticated,
 * access-controlled download endpoint below — never a predictable public URL.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PatientDocumentServiceImpl implements PatientDocumentService {

    private static final Set<String> ALLOWED_TYPES = Set.of("pdf", "jpg", "jpeg", "png");

    private final PatientDocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final AccessControlService accessControl;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Override
    @Transactional
    public PatientDocumentResponse upload(Integer patientId, String documentName, String documentType,
                                           MultipartFile file, String description, Integer uploadedByUserId) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file provided");
        }
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
        String extension = getExtension(originalName).toLowerCase();
        if (!ALLOWED_TYPES.contains(extension)) {
            throw new BadRequestException("Only PDF, JPG, and PNG files are allowed");
        }

        Patient patient = accessControl.loadAccessiblePatient(patientId);
        DocumentType type;
        try {
            type = documentType != null ? DocumentType.valueOf(documentType) : DocumentType.OTHER;
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown document type: " + documentType);
        }
        User uploadedBy = uploadedByUserId != null ? userRepository.findById(uploadedByUserId).orElse(null) : null;

        try {
            Path dir = Paths.get(uploadDir, "documents");
            Files.createDirectories(dir);
            String storedName = UUID.randomUUID() + "." + extension;
            Path target = dir.resolve(storedName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            PatientDocument document = PatientDocument.builder()
                    .patient(patient)
                    .documentName(documentName != null && !documentName.isBlank() ? documentName.trim() : originalName)
                    .documentType(type)
                    .fileName(originalName)
                    .filePath(target.toString())
                    .fileType(extension)
                    .fileSize((int) file.getSize())
                    .description(description)
                    .uploadedBy(uploadedBy)
                    .build();
            document = documentRepository.save(document);
            return toResponse(document);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store uploaded document", e);
        }
    }

    @Override
    public List<PatientDocumentResponse> listForPatient(Integer patientId) {
        accessControl.loadAccessiblePatient(patientId);
        return documentRepository.findByPatient_PatientIdOrderByUploadedAtDesc(patientId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public Resource download(Integer documentId) {
        PatientDocument document = findDocument(documentId);
        Path path = Paths.get(document.getFilePath());
        if (!Files.exists(path)) {
            throw new ResourceNotFoundException("Document file missing on disk: " + documentId);
        }
        return new FileSystemResource(path);
    }

    @Override
    public PreparedDownload prepareDownload(Integer documentId) {
        PatientDocument document = findDocument(documentId);
        Path path = Paths.get(document.getFilePath());
        if (!Files.exists(path)) {
            throw new ResourceNotFoundException("Document file missing on disk: " + documentId);
        }
        String ext = document.getFileType() == null ? "" : document.getFileType().toLowerCase();
        String patientName = document.getPatient().getFullName();
        String typeLabel = document.getDocumentType().getLabel();

        if ("pdf".equals(ext)) {
            return new PreparedDownload(new FileSystemResource(path),
                    FileNames.documentDownloadName(patientName, typeLabel, "pdf"), "application/pdf");
        }
        try {
            byte[] pdf = ImageToPdf.convert(Files.readAllBytes(path)); // in-memory copy; original file untouched
            return new PreparedDownload(new ByteArrayResource(pdf),
                    FileNames.documentDownloadName(patientName, typeLabel, "pdf"), "application/pdf");
        } catch (IOException e) {
            throw new BadRequestException("This image could not be converted to PDF. Please re-upload it as a PDF, JPG or PNG.");
        }
    }

    @Override
    public String getFileName(Integer documentId) {
        return findDocument(documentId).getFileName();
    }

    @Override
    public void assertDownloadAllowed(Integer documentId, Integer requestingUserId, String requestingUserRole) {
        PatientDocument document = findDocument(documentId);

        // Assigned-patient rule first (ADMIN passes, DOCTOR/NURSE must be assigned to the patient)...
        accessControl.assertCanAccessPatient(document.getPatient());

        // ...then the same extra nurse rule used for test-report downloads.
        if (!"NURSE".equals(requestingUserRole)) {
            return;
        }

        User uploader = document.getUploadedBy();
        boolean uploadedBySelf = uploader != null && uploader.getUserId().equals(requestingUserId);
        boolean uploadedByDoctor = uploader != null && uploader.getRole() != null
                && "DOCTOR".equals(uploader.getRole().getRoleName().name());

        if (!uploadedBySelf && !uploadedByDoctor) {
            throw new AccessDeniedException(
                    "Nurses can only download documents they uploaded, or documents uploaded by a doctor");
        }
    }

    private PatientDocument findDocument(Integer documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
    }

    private String getExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx >= 0 ? filename.substring(idx + 1) : "";
    }

    private PatientDocumentResponse toResponse(PatientDocument d) {
        User uploader = d.getUploadedBy();
        return PatientDocumentResponse.builder()
                .documentId(d.getDocumentId())
                .patientId(d.getPatient().getPatientId())
                .documentName(d.getDocumentName())
                .documentType(d.getDocumentType().name())
                .fileName(d.getFileName())
                .fileType(d.getFileType())
                .fileSize(d.getFileSize())
                .description(d.getDescription())
                .uploadedByName(uploader != null ? uploader.getFullName() : null)
                .uploadedByRole(uploader != null && uploader.getRole() != null ? uploader.getRole().getRoleName().name() : null)
                .uploadedAt(d.getUploadedAt())
                .downloadUrl("/patient-documents/" + d.getDocumentId() + "/download")
                .downloadFileName(FileNames.documentDownloadName(d.getPatient().getFullName(), d.getDocumentType().getLabel(), "pdf"))
                .build();
    }
}
