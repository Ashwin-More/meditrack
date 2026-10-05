package com.meditrack.service;

import com.meditrack.dto.LoginRequest;
import com.meditrack.entity.Role;
import com.meditrack.entity.User;
import com.meditrack.enums.RoleName;
import com.meditrack.enums.UserStatus;
import com.meditrack.exception.BadRequestException;
import com.meditrack.exception.UnauthorizedException;
import com.meditrack.dto.RegisterRequest;
import com.meditrack.repository.DoctorRepository;
import com.meditrack.repository.NurseRepository;
import com.meditrack.repository.RoleRepository;
import com.meditrack.repository.UserRepository;
import com.meditrack.security.JwtService;
import com.meditrack.entity.RefreshToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    @Mock private SmsService smsService;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userRepository, roleRepository, doctorRepository,
                nurseRepository, passwordEncoder, authenticationManager, jwtService, auditLogService,
                refreshTokenService, emailTokenService, emailService, smsService);
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
        when(userRepository.findByEmail("ghost")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("ghost")).thenReturn(Optional.empty());

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
    void login_byEmailIdentifier_isResolvedTheSameAsUsername() {
        User user = buildUser(UserStatus.ACTIVE);
        when(userRepository.findByUsername("dr@meditrack.local")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("dr@meditrack.local")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(any())).thenReturn("fake.jwt.token");
        when(refreshTokenService.createRefreshToken(any()))
                .thenReturn(RefreshToken.builder().token("fake-refresh-token").build());

        LoginRequest req = new LoginRequest();
        req.setUsername("dr@meditrack.local");
        req.setPassword("Password@123");

        var response = authService.login(req, null);

        assertEquals("drrahul", response.getUsername());
        // The authentication manager must be given the resolved username, not the raw email.
        verify(authenticationManager).authenticate(argThat(token ->
                "drrahul".equals(token.getPrincipal())));
    }

    @Test
    void login_pendingAccount_wrongPassword_doesNotRevealStatus() {
        when(userRepository.findByUsername("drrahul")).thenReturn(Optional.of(buildUser(UserStatus.PENDING)));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        LoginRequest req = new LoginRequest();
        req.setUsername("drrahul");
        req.setPassword("wrong");

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () -> authService.login(req, null));
        assertEquals("Invalid username, email, phone, or password", ex.getMessage());
    }

    @Test
    void login_unverifiedAccount_isRejectedEvenWhenActive() {
        User user = buildUser(UserStatus.ACTIVE);
        user.setEmailVerified(false);
        when(userRepository.findByUsername("drrahul")).thenReturn(Optional.of(user));

        LoginRequest req = new LoginRequest();
        req.setUsername("drrahul");
        req.setPassword("Password@123");

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () -> authService.login(req, null));
        assertTrue(ex.getMessage().contains("verify your account"));
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void verifyOtp_marksUserVerified() {
        User user = buildUser(UserStatus.PENDING);
        user.setEmailVerified(false);
        when(userRepository.findByUsername("drrahul")).thenReturn(Optional.of(user));

        String message = authService.verifyOtp("drrahul", "123456");

        verify(emailTokenService).consume(user, "123456", com.meditrack.enums.TokenType.EMAIL_VERIFY);
        assertTrue(user.isEmailVerified());
        assertTrue(message.contains("admin approval"));
        verify(userRepository).save(user);
    }

    @Test
    void resetPassword_updatesHash_andRevokesSessions() {
        User user = buildUser(UserStatus.ACTIVE);
        when(userRepository.findByUsername("drrahul")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPass123")).thenReturn("new-hash");

        authService.resetPassword("drrahul", "123456", "NewPass123");

        assertEquals("new-hash", user.getPasswordHash());
        verify(refreshTokenService).revokeAllForUser(1);
    }


    @Test
    void login_staffAccountThroughSuperAdminPortal_isRejected() {
        User user = buildUser(UserStatus.ACTIVE); // role = DOCTOR
        when(userRepository.findByUsername("drrahul")).thenReturn(Optional.of(user));

        LoginRequest req = new LoginRequest();
        req.setUsername("drrahul");
        req.setPassword("Password@123");
        req.setPortal("SUPER_ADMIN");

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () -> authService.login(req, null));
        assertTrue(ex.getMessage().contains("not a Super Admin account"));
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void login_superAdminAccountThroughStaffPortal_isRejected() {
        Role superAdminRole = Role.builder().roleId(1).roleName(RoleName.SUPER_ADMIN).build();
        User user = User.builder()
                .userId(9).username("superadmin").passwordHash("hashed")
                .firstName("Institute").lastName("Owner")
                .role(superAdminRole).status(UserStatus.ACTIVE).emailVerified(true).build();
        when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(user));

        LoginRequest req = new LoginRequest();
        req.setUsername("superadmin");
        req.setPassword("SuperAdmin@123");
        req.setPortal("STAFF");

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () -> authService.login(req, null));
        assertTrue(ex.getMessage().contains("Super Admin login"));
    }

    @Test
    void login_correctPortal_succeeds() {
        User user = buildUser(UserStatus.ACTIVE); // role = DOCTOR
        when(userRepository.findByUsername("drrahul")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(any())).thenReturn("fake.jwt.token");
        when(refreshTokenService.createRefreshToken(any()))
                .thenReturn(RefreshToken.builder().token("fake-refresh-token").build());

        LoginRequest req = new LoginRequest();
        req.setUsername("drrahul");
        req.setPassword("Password@123");
        req.setPortal("STAFF");

        var response = authService.login(req, null);
        assertEquals("fake.jwt.token", response.getToken());
    }

    @Test
    void forgotPassword_sendsTheCodeByEmail_andNeverReturnsIt() {
        User user = buildUser(UserStatus.ACTIVE);
        when(userRepository.findByUsername("drrahul")).thenReturn(Optional.of(user));
        when(emailTokenService.issue(user, com.meditrack.enums.TokenType.PASSWORD_RESET)).thenReturn("123456");

        authService.forgotPassword("drrahul"); // void: there is no way for the code to reach the API response

        verify(emailService).sendPasswordResetOtp(user, "123456");
    }

    @Test
    void forgotPassword_unknownIdentifier_isSilent() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("ghost")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("ghost")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> authService.forgotPassword("ghost"));
        verifyNoInteractions(emailService, smsService);
    }

    @Test
    void requestLoginOtp_byPhone_smsNotDelivered_fallsBackToEmail() {
        User user = buildUser(UserStatus.ACTIVE);
        user.setEmail("dr@example.com");
        user.setPhone("+919000000002");
        when(userRepository.findByUsername("+919000000002")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("+919000000002")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("+919000000002")).thenReturn(Optional.of(user));
        when(emailTokenService.issue(user, com.meditrack.enums.TokenType.LOGIN_OTP)).thenReturn("654321");

        authService.requestLoginOtp("+919000000002", "STAFF");
        verify(smsService).sendLoginOtp(user, "654321");
        verify(emailService).sendLoginOtp(user, "654321"); // fallback, so the code still reaches an inbox
    }

    @Test
    void requestLoginOtp_byPhone_smsDelivered_doesNotEmail() {
        User user = buildUser(UserStatus.ACTIVE);
        user.setPhone("+919000000002");
        when(userRepository.findByUsername("+919000000002")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("+919000000002")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("+919000000002")).thenReturn(Optional.of(user));
        when(emailTokenService.issue(user, com.meditrack.enums.TokenType.LOGIN_OTP)).thenReturn("654321");
        when(smsService.sendLoginOtp(user, "654321")).thenReturn(true);

        authService.requestLoginOtp("+919000000002", "STAFF");
        verifyNoInteractions(emailService);
    }

    @Test
    void resendOtp_withinCooldown_sendsNothing_andIssuesNothing() {
        User user = buildUser(UserStatus.PENDING);
        user.setEmailVerified(false);
        when(userRepository.findByUsername("drrahul")).thenReturn(Optional.of(user));
        when(emailTokenService.isInResendCooldown(user, com.meditrack.enums.TokenType.EMAIL_VERIFY)).thenReturn(true);

        authService.resendOtp("drrahul");

        verify(emailTokenService, never()).issue(any(), any());
        verifyNoInteractions(emailService);
    }

    @Test
    void resendOtp_issuesANewCode_forAnUnverifiedAccount() {
        User user = buildUser(UserStatus.PENDING);
        user.setEmailVerified(false);
        when(userRepository.findByUsername("drrahul")).thenReturn(Optional.of(user));
        when(emailTokenService.issue(user, com.meditrack.enums.TokenType.EMAIL_VERIFY)).thenReturn("111222");

        authService.resendOtp("drrahul");

        verify(emailService).sendVerificationOtp(user, "111222");
    }

    @Test
    void verifyOtp_verifiesTheEmail_butDoesNotApproveThePendingAccount() {
        User user = buildUser(UserStatus.PENDING);
        user.setEmailVerified(false);
        when(userRepository.findByUsername("drrahul")).thenReturn(Optional.of(user));

        String message = authService.verifyOtp("drrahul", "123456");

        assertTrue(user.isEmailVerified());
        assertEquals(UserStatus.PENDING, user.getStatus()); // approval is a separate admin step
        assertTrue(message.startsWith("Email verified successfully."));
        verify(emailTokenService).consume(user, "123456", com.meditrack.enums.TokenType.EMAIL_VERIFY);
    }

    @Test
    void verifyOtp_wrongCode_doesNotVerifyTheAccount() {
        User user = buildUser(UserStatus.PENDING);
        user.setEmailVerified(false);
        when(userRepository.findByUsername("drrahul")).thenReturn(Optional.of(user));
        doThrow(new BadRequestException(EmailTokenService.MSG_INVALID))
                .when(emailTokenService).consume(user, "000000", com.meditrack.enums.TokenType.EMAIL_VERIFY);

        assertThrows(BadRequestException.class, () -> authService.verifyOtp("drrahul", "000000"));
        assertFalse(user.isEmailVerified());
    }

    @Test
    void verifyOtp_unknownAccount_givesTheSameMessageAsAWrongCode() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("ghost")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("ghost")).thenReturn(Optional.empty());

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.verifyOtp("ghost", "123456"));
        assertEquals(EmailTokenService.MSG_INVALID, ex.getMessage());
    }

    @Test
    void unverifiedAccount_cannotLogIn_evenWithTheCorrectPassword() {
        User user = buildUser(UserStatus.PENDING);
        user.setEmailVerified(false);
        when(userRepository.findByUsername("drrahul")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password@123", "hashed")).thenReturn(true);

        LoginRequest req = new LoginRequest();
        req.setUsername("drrahul");
        req.setPassword("Password@123");
        assertThrows(UnauthorizedException.class, () -> authService.login(req, null));
        verify(authenticationManager, never()).authenticate(any());
    }

    // ---------- registration: manual username ----------

    private RegisterRequest registerRequest(String username, String role) {
        RegisterRequest r = new RegisterRequest();
        r.setUsername(username);
        r.setEmail("new@example.com");
        r.setPassword("Password@123");
        r.setFirstName("New");
        r.setLastName("User");
        r.setPhone("+919000000009");
        r.setRole(role);
        return r;
    }

    @Test
    void register_doctor_storesTheChosenUsernameExactlyAsEntered_asPending() {
        when(userRepository.existsByUsername("Dr.Rahul_7")).thenReturn(false);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.existsByPhone("+919000000009")).thenReturn(false);
        when(roleRepository.findByRoleName(RoleName.DOCTOR)).thenReturn(Optional.of(Role.builder().roleId(2).roleName(RoleName.DOCTOR).build()));
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register(registerRequest("Dr.Rahul_7", "DOCTOR"));

        org.mockito.ArgumentCaptor<User> saved = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals("Dr.Rahul_7", saved.getValue().getUsername());
        assertEquals(UserStatus.PENDING, saved.getValue().getStatus());
        assertFalse(saved.getValue().isEmailVerified());
        verify(userRepository, never()).count(); // no auto-generation
    }

    @Test
    void register_nurse_duplicateUsername_isRejectedWithTheExactMessage() {
        when(userRepository.existsByUsername("nursepriya")).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> authService.register(registerRequest("nursepriya", "NURSE")));
        assertEquals("Username already exists. Please choose another username.", ex.getMessage());
        verify(userRepository, never()).save(any());
    }
}
