package com.meditrack.service;

import com.meditrack.dto.LoginRequest;
import com.meditrack.entity.Role;
import com.meditrack.entity.User;
import com.meditrack.enums.RoleName;
import com.meditrack.enums.TokenType;
import com.meditrack.enums.UserStatus;
import com.meditrack.exception.UnauthorizedException;
import com.meditrack.repository.DoctorRepository;
import com.meditrack.repository.NurseRepository;
import com.meditrack.repository.RoleRepository;
import com.meditrack.repository.UserRepository;
import com.meditrack.security.JwtService;
import com.meditrack.service.AuthServiceImpl;
import com.meditrack.service.RefreshTokenService;
import com.meditrack.entity.RefreshToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private DoctorRepository doctorRepository;
    @Mock private NurseRepository nurseRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private AuditLogService auditLogService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private EmailTokenService emailTokenService;
    @Mock private EmailService emailService;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userRepository, roleRepository, doctorRepository,
                nurseRepository, passwordEncoder, authenticationManager, jwtService, auditLogService,
                refreshTokenService, emailTokenService, emailService);
    }

    private User buildUser(UserStatus status) {
        Role role = Role.builder().roleId(2).roleName(RoleName.DOCTOR).build();
        return User.builder()
                .userId(1)
                .username("drrahul")
                .email("dr@meditrack.local")
                .passwordHash("hashed")
                .firstName("Rahul")
                .lastName("Sharma")
                .role(role)
                .status(status)
                .emailVerified(true)
                .build();
    }

    @Test
    void login_rejectsPendingAccount() {
        when(userRepository.findByUsername("drrahul")).thenReturn(Optional.of(buildUser(UserStatus.PENDING)));
        when(passwordEncoder.matches("Password@123", "hashed")).thenReturn(true);

        LoginRequest req = new LoginRequest();
        req.setUsername("drrahul");
        req.setPassword("Password@123");

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> authService.login(req, null));
        assertEquals("Your account is awaiting admin approval", ex.getMessage());
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void login_rejectsDisabledAccount() {
        when(userRepository.findByUsername("drrahul")).thenReturn(Optional.of(buildUser(UserStatus.DISABLED)));
        when(passwordEncoder.matches("Password@123", "hashed")).thenReturn(true);

        LoginRequest req = new LoginRequest();
        req.setUsername("drrahul");
        req.setPassword("Password@123");

        assertThrows(UnauthorizedException.class, () -> authService.login(req, null));
    }

    @Test
    void login_unknownUsername_throwsUnauthorized() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        LoginRequest req = new LoginRequest();
        req.setUsername("ghost");
        req.setPassword("whatever");

        assertThrows(UnauthorizedException.class, () -> authService.login(req, null));
    }

    @Test
    void login_activeAccount_returnsToken() {
        User user = buildUser(UserStatus.ACTIVE);
        when(userRepository.findByUsername("drrahul")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(any())).thenReturn("fake.jwt.token");
        when(refreshTokenService.createRefreshToken(any()))
                .thenReturn(RefreshToken.builder().token("fake-refresh-token").build());

        LoginRequest req = new LoginRequest();
        req.setUsername("drrahul");
        req.setPassword("Password@123");

        var response = authService.login(req, null);

        assertEquals("fake.jwt.token", response.getToken());
        assertEquals("fake-refresh-token", response.getRefreshToken());
        assertEquals("DOCTOR", response.getRole());
        assertEquals("drrahul", response.getUsername());
        verify(authenticationManager, times(1)).authenticate(any());
        verify(userRepository, atLeastOnce()).save(any(User.class));
    }

    @Test
    void login_pendingAccount_wrongPassword_doesNotRevealStatus() {
        when(userRepository.findByUsername("drrahul")).thenReturn(Optional.of(buildUser(UserStatus.PENDING)));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        LoginRequest req = new LoginRequest();
        req.setUsername("drrahul");
        req.setPassword("wrong");

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () -> authService.login(req, null));
        assertEquals("Invalid username or password", ex.getMessage());
    }

    @Test
    void login_unverifiedEmail_isRejectedEvenWhenActive() {
        User user = buildUser(UserStatus.ACTIVE);
        user.setEmailVerified(false);
        when(userRepository.findByUsername("drrahul")).thenReturn(Optional.of(user));

        LoginRequest req = new LoginRequest();
        req.setUsername("drrahul");
        req.setPassword("Password@123");

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () -> authService.login(req, null));
        assertTrue(ex.getMessage().contains("verify your email"));
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void verifyEmail_marksUserVerified() {
        User user = buildUser(UserStatus.PENDING);
        user.setEmailVerified(false);
        when(emailTokenService.consume("raw-token", TokenType.EMAIL_VERIFY)).thenReturn(user);

        String message = authService.verifyEmail("raw-token");

        assertTrue(user.isEmailVerified());
        assertTrue(message.contains("admin approval"));
        verify(userRepository).save(user);
    }

    @Test
    void resetPassword_updatesHash_andRevokesSessions() {
        User user = buildUser(UserStatus.ACTIVE);
        when(emailTokenService.consume("raw-token", TokenType.PASSWORD_RESET)).thenReturn(user);
        when(passwordEncoder.encode("NewPass123")).thenReturn("new-hash");

        authService.resetPassword("raw-token", "NewPass123");

        assertEquals("new-hash", user.getPasswordHash());
        verify(refreshTokenService).revokeAllForUser(1);
    }

    @Test
    void forgotPassword_unknownEmail_isSilent() {
        when(userRepository.findByEmail("nobody@x.com")).thenReturn(Optional.empty());

        authService.forgotPassword("nobody@x.com");

        verifyNoInteractions(emailService);
    }
}
