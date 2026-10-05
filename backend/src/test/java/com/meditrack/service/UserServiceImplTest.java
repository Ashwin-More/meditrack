package com.meditrack.service;

import com.meditrack.entity.Role;
import com.meditrack.entity.User;
import com.meditrack.enums.RoleName;
import com.meditrack.exception.BadRequestException;
import com.meditrack.repository.*;
import com.meditrack.security.AccessControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private DoctorRepository doctorRepository;
    @Mock private NurseRepository nurseRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditLogService auditLogService;
    @Mock private EmailTokenService emailTokenService;
    @Mock private EmailService emailService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private AccessControlService accessControl;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userRepository, roleRepository, doctorRepository, nurseRepository,
                passwordEncoder, auditLogService, emailTokenService, emailService, refreshTokenService, accessControl);
    }

    private User user() {
        return User.builder().userId(4).username("drrahul").passwordHash("old-hash")
                .role(Role.builder().roleName(RoleName.DOCTOR).build())
                .build();
    }

    @Test
    void changePassword_wrongCurrentPassword_isRejected() {
        when(userRepository.findByUsername("drrahul")).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches("bad", "old-hash")).thenReturn(false);

        assertThrows(BadRequestException.class, () -> service.changePassword("drrahul", "bad", "NewPass123"));
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    void changePassword_success_updatesHash_andRevokesSessions() {
        User u = user();
        when(userRepository.findByUsername("drrahul")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("OldPass123", "old-hash")).thenReturn(true);
        when(passwordEncoder.matches("NewPass123", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("NewPass123")).thenReturn("new-hash");

        service.changePassword("drrahul", "OldPass123", "NewPass123");

        assertEquals("new-hash", u.getPasswordHash());
        verify(refreshTokenService).revokeAllForUser(4);
    }

    @Test
    void resendVerification_alreadyVerified_isRejected() {
        User u = user();
        u.setEmailVerified(true);
        when(userRepository.findById(4)).thenReturn(Optional.of(u));

        assertThrows(BadRequestException.class, () -> service.resendVerification(4));
        verifyNoInteractions(emailService);
    }

    @Test
    void disableUser_adminTargetBlockedForNonSuperAdminActor() {
        User target = User.builder().userId(9).username("otheradmin").passwordHash("h")
                .role(Role.builder().roleName(RoleName.ADMIN).build())
                .status(com.meditrack.enums.UserStatus.ACTIVE).build();
        when(userRepository.findById(9)).thenReturn(Optional.of(target));

        com.meditrack.security.UserPrincipal actingAdmin = new com.meditrack.security.UserPrincipal(
                User.builder().userId(1).username("admin").passwordHash("h")
                        .role(Role.builder().roleName(RoleName.ADMIN).build())
                        .status(com.meditrack.enums.UserStatus.ACTIVE).build());
        when(accessControl.currentPrincipal()).thenReturn(actingAdmin);

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.disableUser(9));
    }

    @Test
    void disableUser_cannotDisableSelf_evenAsSuperAdmin() {
        User target = User.builder().userId(1).username("boss").passwordHash("h")
                .role(Role.builder().roleName(RoleName.SUPER_ADMIN).build())
                .status(com.meditrack.enums.UserStatus.ACTIVE).build();
        when(userRepository.findById(1)).thenReturn(Optional.of(target));

        com.meditrack.security.UserPrincipal actingSelf = new com.meditrack.security.UserPrincipal(target);
        when(accessControl.currentPrincipal()).thenReturn(actingSelf);

        assertThrows(BadRequestException.class, () -> service.disableUser(1));
    }

    @Test
    void disableUser_cannotDisableLastActiveSuperAdmin() {
        User target = User.builder().userId(2).username("boss2").passwordHash("h")
                .role(Role.builder().roleName(RoleName.SUPER_ADMIN).build())
                .status(com.meditrack.enums.UserStatus.ACTIVE).build();
        when(userRepository.findById(2)).thenReturn(Optional.of(target));
        when(userRepository.countByRole_RoleNameAndStatus(RoleName.SUPER_ADMIN, com.meditrack.enums.UserStatus.ACTIVE))
                .thenReturn(1L);

        com.meditrack.security.UserPrincipal actingSuperAdmin = new com.meditrack.security.UserPrincipal(
                User.builder().userId(1).username("otherboss").passwordHash("h")
                        .role(Role.builder().roleName(RoleName.SUPER_ADMIN).build())
                        .status(com.meditrack.enums.UserStatus.ACTIVE).build());
        when(accessControl.currentPrincipal()).thenReturn(actingSuperAdmin);

        assertThrows(BadRequestException.class, () -> service.disableUser(2));
    }
}
