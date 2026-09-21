package com.meditrack.service;

import com.meditrack.entity.User;
import com.meditrack.exception.BadRequestException;
import com.meditrack.repository.*;
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

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userRepository, roleRepository, doctorRepository, nurseRepository,
                passwordEncoder, auditLogService, emailTokenService, emailService, refreshTokenService);
    }

    private User user() {
        return User.builder().userId(4).username("drrahul").passwordHash("old-hash").build();
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
}
