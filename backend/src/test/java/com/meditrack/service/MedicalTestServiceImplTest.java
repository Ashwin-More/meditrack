package com.meditrack.service;

import com.meditrack.entity.MedicalTest;
import com.meditrack.entity.Patient;
import com.meditrack.entity.Role;
import com.meditrack.security.AccessControlService;
import com.meditrack.entity.TestReport;
import com.meditrack.entity.User;
import com.meditrack.enums.RoleName;
import com.meditrack.repository.*;
import com.meditrack.service.MedicalTestServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicalTestServiceImplTest {

    @Mock private MedicalTestRepository testRepository;
    @Mock private VisitRepository visitRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private DoctorRepository doctorRepository;
    @Mock private TestReportRepository reportRepository;
    @Mock private UserRepository userRepository;
    @Mock private AccessControlService accessControl;

    private MedicalTestServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MedicalTestServiceImpl(testRepository, visitRepository, patientRepository,
                doctorRepository, reportRepository, userRepository, accessControl);
    }

    private User userWithRole(int id, RoleName roleName) {
        Role role = Role.builder().roleId(1).roleName(roleName).build();
        return User.builder().userId(id).role(role).username("u" + id).build();
    }

    private MedicalTest testForPatient() {
        return MedicalTest.builder().patient(Patient.builder().patientId(1).build()).build();
    }

    @Test
    void adminAndDoctor_canDownloadAnyReportOfAnAccessiblePatient() {
        User doctor = userWithRole(20, RoleName.DOCTOR);
        TestReport report = TestReport.builder().reportId(99).test(testForPatient()).uploadedBy(doctor).build();
        when(reportRepository.findById(99)).thenReturn(Optional.of(report));

        assertDoesNotThrow(() -> service.assertDownloadAllowed(99, 5, "ADMIN"));
        assertDoesNotThrow(() -> service.assertDownloadAllowed(99, 5, "DOCTOR"));
    }

    @Test
    void download_isBlocked_whenPatientIsNotAssignedToCaller() {
        TestReport report = TestReport.builder().reportId(50).test(testForPatient()).build();
        when(reportRepository.findById(50)).thenReturn(Optional.of(report));
        org.mockito.Mockito.doThrow(new com.meditrack.exception.NotAssignedException("not assigned"))
                .when(accessControl).assertCanAccessPatient(any());

        assertThrows(AccessDeniedException.class, () -> service.assertDownloadAllowed(50, 5, "DOCTOR"));
    }

    @Test
    void nurse_canDownloadOwnUpload() {
        User nurse = userWithRole(10, RoleName.NURSE);
        TestReport report = TestReport.builder().reportId(1).test(testForPatient()).uploadedBy(nurse).build();
        when(reportRepository.findById(1)).thenReturn(Optional.of(report));

        assertDoesNotThrow(() -> service.assertDownloadAllowed(1, 10, "NURSE"));
    }

    @Test
    void nurse_canDownloadReportUploadedByDoctor() {
        User doctorUser = userWithRole(20, RoleName.DOCTOR);
        TestReport report = TestReport.builder().reportId(2).test(testForPatient()).uploadedBy(doctorUser).build();
        when(reportRepository.findById(2)).thenReturn(Optional.of(report));

        assertDoesNotThrow(() -> service.assertDownloadAllowed(2, 10, "NURSE"));
    }

    @Test
    void nurse_cannotDownloadReportUploadedByAnotherNurse() {
        User otherNurse = userWithRole(30, RoleName.NURSE);
        TestReport report = TestReport.builder().reportId(3).test(testForPatient()).uploadedBy(otherNurse).build();
        when(reportRepository.findById(3)).thenReturn(Optional.of(report));

        assertThrows(AccessDeniedException.class,
                () -> service.assertDownloadAllowed(3, 10, "NURSE"));
    }
}
