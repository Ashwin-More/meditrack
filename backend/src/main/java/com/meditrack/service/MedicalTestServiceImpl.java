package com.meditrack.service;

import com.meditrack.dto.MedicalTestRequest;
import com.meditrack.dto.MedicalTestResponse;
import com.meditrack.dto.TestReportResponse;
import com.meditrack.entity.*;
import com.meditrack.enums.TestStatus;
import com.meditrack.exception.BadRequestException;
import com.meditrack.exception.ResourceNotFoundException;
import com.meditrack.repository.*;
import com.meditrack.security.AccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicalTestServiceImpl implements MedicalTestService {

    private static final Set<String> ALLOWED_TYPES = Set.of("pdf", "jpg", "jpeg", "png");

    private final MedicalTestRepository testRepository;
    private final VisitRepository visitRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final TestReportRepository reportRepository;
    private final UserRepository userRepository;
    private final AccessControlService accessControl;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Override
    @Transactional
    public MedicalTestResponse orderTest(MedicalTestRequest request) {
        Visit visit = visitRepository.findById(request.getVisitId())
                .orElseThrow(() -> new ResourceNotFoundException("Visit not found: " + request.getVisitId()));
        Patient patient = accessControl.loadAccessiblePatient(request.getPatientId());
        accessControl.assertActingAsDoctor(request.getDoctorId());
        if (!visit.getPatient().getPatientId().equals(patient.getPatientId())) {
            throw new BadRequestException("That visit does not belong to this patient");
        }
        Doctor doctor = doctorRepository.findById(request.getDoctorId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + request.getDoctorId()));

        MedicalTest test = MedicalTest.builder()
                .visit(visit)
                .patient(patient)
                .doctor(doctor)
                .testName(request.getTestName())
                .testType(request.getTestType())
                .status(TestStatus.PENDING)
                .build();

        test = testRepository.save(test);
        return toResponse(test);
    }

    @Override
    @Transactional
    public MedicalTestResponse updateResult(Integer testId, MedicalTestRequest request) {
        MedicalTest test = findTest(testId);
        accessControl.assertCanAccessPatient(test.getPatient());
        test.setResult(request.getResult());
        test.setNormalRange(request.getNormalRange());
        test.setRemarks(request.getRemarks());
        test.setStatus(request.getStatus() != null ? TestStatus.valueOf(request.getStatus()) : TestStatus.COMPLETED);
        test = testRepository.save(test);
        return toResponse(test);
    }

    @Override
    public MedicalTestResponse getTest(Integer testId) {
        MedicalTest test = findTest(testId);
        accessControl.assertCanAccessPatient(test.getPatient());
        return toResponse(test);
    }

    @Override
    public List<MedicalTestResponse> getTestsForPatient(Integer patientId) {
        accessControl.loadAccessiblePatient(patientId);
        return testRepository.findByPatient_PatientIdOrderByTestDateDesc(patientId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public TestReportResponse uploadReport(Integer testId, MultipartFile file, String description, Integer uploadedByUserId) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file provided");
        }
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "report";
        String extension = getExtension(originalName).toLowerCase();
        if (!ALLOWED_TYPES.contains(extension)) {
            throw new BadRequestException("Only PDF, JPG, and PNG files are allowed");
        }

        MedicalTest test = findTest(testId);
        accessControl.assertCanAccessPatient(test.getPatient());
        User uploadedBy = uploadedByUserId != null ? userRepository.findById(uploadedByUserId).orElse(null) : null;

        try {
            Path dir = Paths.get(uploadDir);
            Files.createDirectories(dir);
            String storedName = UUID.randomUUID() + "." + extension;
            Path target = dir.resolve(storedName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            TestReport report = TestReport.builder()
                    .test(test)
                    .fileName(originalName)
                    .filePath(target.toString())
                    .fileType(extension)
                    .fileSize((int) file.getSize())
                    .uploadedBy(uploadedBy)
                    .description(description)
                    .build();
            report = reportRepository.save(report);

            return TestReportResponse.builder()
                    .reportId(report.getReportId())
                    .fileName(report.getFileName())
                    .fileType(report.getFileType())
                    .fileSize(report.getFileSize())
                    .uploadedByName(uploadedBy != null ? uploadedBy.getFullName() : null)
                    .uploadedAt(report.getUploadedAt())
                    .description(report.getDescription())
                    .downloadUrl("/reports/" + report.getReportId() + "/download")
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store uploaded file", e);
        }
    }

    @Override
    public Resource downloadReport(Integer reportId) {
        TestReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found: " + reportId));
        Path path = Paths.get(report.getFilePath());
        if (!Files.exists(path)) {
            throw new ResourceNotFoundException("Report file missing on disk: " + reportId);
        }
        return new FileSystemResource(path);
    }

    @Override
    public String getReportFileName(Integer reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found: " + reportId))
                .getFileName();
    }

    @Override
    public void assertDownloadAllowed(Integer reportId, Integer requestingUserId, String requestingUserRole) {
        TestReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found: " + reportId));

        // Assigned-patient rule first (ADMIN passes, DOCTOR/NURSE must be assigned to the patient)...
        accessControl.assertCanAccessPatient(report.getTest().getPatient());

        // ...then the extra nurse rule below.
        if (!"NURSE".equals(requestingUserRole)) {
            return;
        }

        User uploader = report.getUploadedBy();
        boolean uploadedBySelf = uploader != null && uploader.getUserId().equals(requestingUserId);
        boolean uploadedByDoctor = uploader != null && uploader.getRole() != null
                && "DOCTOR".equals(uploader.getRole().getRoleName().name());

        if (!uploadedBySelf && !uploadedByDoctor) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Nurses can only download reports they uploaded, or reports uploaded by a doctor");
        }
    }

    private MedicalTest findTest(Integer testId) {
        return testRepository.findById(testId)
                .orElseThrow(() -> new ResourceNotFoundException("Medical test not found: " + testId));
    }

    private String getExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx >= 0 ? filename.substring(idx + 1) : "";
    }

    private MedicalTestResponse toResponse(MedicalTest t) {
        List<TestReportResponse> reports = t.getReports().stream().map(r -> TestReportResponse.builder()
                .reportId(r.getReportId())
                .fileName(r.getFileName())
                .fileType(r.getFileType())
                .fileSize(r.getFileSize())
                .uploadedByName(r.getUploadedBy() != null ? r.getUploadedBy().getFullName() : null)
                .uploadedAt(r.getUploadedAt())
                .description(r.getDescription())
                .downloadUrl("/reports/" + r.getReportId() + "/download")
                .build()).collect(Collectors.toList());

        return MedicalTestResponse.builder()
                .testId(t.getTestId())
                .visitId(t.getVisit().getVisitId())
                .patientId(t.getPatient().getPatientId())
                .doctorId(t.getDoctor().getDoctorId())
                .testName(t.getTestName())
                .testType(t.getTestType())
                .testDate(t.getTestDate())
                .result(t.getResult())
                .normalRange(t.getNormalRange())
                .remarks(t.getRemarks())
                .status(t.getStatus().name())
                .reports(reports)
                .build();
    }
}
