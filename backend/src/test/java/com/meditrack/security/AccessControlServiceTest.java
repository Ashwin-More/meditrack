package com.meditrack.security;

import com.meditrack.entity.Doctor;
import com.meditrack.entity.Nurse;
import com.meditrack.entity.Patient;
import com.meditrack.entity.Role;
import com.meditrack.entity.User;
import com.meditrack.enums.RoleName;
import com.meditrack.enums.UserStatus;
import com.meditrack.exception.NotAssignedException;
import com.meditrack.repository.DoctorRepository;
import com.meditrack.repository.NurseRepository;
import com.meditrack.repository.PatientRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessControlServiceTest {

    @Mock private DoctorRepository doctorRepository;
    @Mock private NurseRepository nurseRepository;
    @Mock private PatientRepository patientRepository;

    private AccessControlService service;

    @BeforeEach
    void setUp() {
        service = new AccessControlService(doctorRepository, nurseRepository, patientRepository);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(int userId, RoleName roleName) {
        User user = User.builder().userId(userId).username("u" + userId).passwordHash("x")
                .role(Role.builder().roleName(roleName).build()).status(UserStatus.ACTIVE).build();
        UserPrincipal principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    void admin_canAccessAnyPatient() {
        loginAs(1, RoleName.ADMIN);
        assertDoesNotThrow(() -> service.assertCanAccessPatient(Patient.builder().build()));
    }

    @Test
    void doctor_canAccessOnlyAssignedPatients() {
        loginAs(2, RoleName.DOCTOR);
        when(doctorRepository.findByUser_UserId(2)).thenReturn(Optional.of(Doctor.builder().doctorId(10).build()));

        Patient mine = Patient.builder().assignedDoctor(Doctor.builder().doctorId(10).build()).build();
        Patient someoneElses = Patient.builder().assignedDoctor(Doctor.builder().doctorId(11).build()).build();
        Patient unassigned = Patient.builder().build();

        assertDoesNotThrow(() -> service.assertCanAccessPatient(mine));
        assertThrows(NotAssignedException.class, () -> service.assertCanAccessPatient(someoneElses));
        assertThrows(NotAssignedException.class, () -> service.assertCanAccessPatient(unassigned));
    }

    @Test
    void nurse_canAccessOnlyAssignedPatients() {
        loginAs(3, RoleName.NURSE);
        when(nurseRepository.findByUser_UserId(3)).thenReturn(Optional.of(Nurse.builder().nurseId(5).build()));

        Patient mine = Patient.builder().assignedNurse(Nurse.builder().nurseId(5).build()).build();
        Patient doctorOnly = Patient.builder().assignedDoctor(Doctor.builder().doctorId(10).build()).build();

        assertDoesNotThrow(() -> service.assertCanAccessPatient(mine));
        assertThrows(NotAssignedException.class, () -> service.assertCanAccessPatient(doctorOnly));
    }

    @Test
    void doctor_cannotActAsAnotherDoctor() {
        loginAs(2, RoleName.DOCTOR);
        when(doctorRepository.findByUser_UserId(2)).thenReturn(Optional.of(Doctor.builder().doctorId(10).build()));

        assertDoesNotThrow(() -> service.assertActingAsDoctor(10));
        assertThrows(NotAssignedException.class, () -> service.assertActingAsDoctor(99));
    }
}
