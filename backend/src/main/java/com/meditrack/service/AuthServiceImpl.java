package com.meditrack.service;

import com.meditrack.dto.LoginRequest;
import com.meditrack.dto.RegisterRequest;
import com.meditrack.dto.AuthResponse;
import com.meditrack.entity.Doctor;
import com.meditrack.entity.Nurse;
import com.meditrack.entity.Role;
import com.meditrack.entity.User;
import com.meditrack.enums.ActiveStatus;
import com.meditrack.enums.AuditAction;
import com.meditrack.enums.OtpChannel;
import com.meditrack.enums.RoleName;
import com.meditrack.enums.TokenType;
import com.meditrack.enums.UserStatus;
import com.meditrack.exception.BadRequestException;
import com.meditrack.exception.TokenRefreshException;
import com.meditrack.exception.UnauthorizedException;
import com.meditrack.repository.DoctorRepository;
import com.meditrack.repository.NurseRepository;
import com.meditrack.repository.RoleRepository;
import com.meditrack.repository.UserRepository;
import com.meditrack.security.JwtService;
import com.meditrack.security.UserPrincipal;
import com.meditrack.util.CodeGenerator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Authentication now uses one-time codes (OTP) instead of clickable email
 * links for email verification and password reset, and accepts a username,
 * email address, OR phone number as the login identifier everywhere
 * ("identifier"). A fully passwordless OTP login is also available via
 * requestLoginOtp / verifyLoginOtp for people who'd rather not type a password.
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** The frontend looks for the phrase 'verify your account' to offer a 'resend code' flow. */
    static final String NOT_VERIFIED_MESSAGE =
            "Please verify your account before logging in. Check your email/SMS for the code, or request a new one";

    static final String USERNAME_TAKEN_MESSAGE = "Username already exists. Please choose another username.";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DoctorRepository doctorRepository;
    private final NurseRepository nurseRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AuditLogService auditLogService;
    private final RefreshTokenService refreshTokenService;
    private final EmailTokenService emailTokenService;
    private final EmailService emailService;
    private final SmsService smsService;

    // ---------------------------------------------------------------
    // Identifier resolution: username, email, or phone all work the same.
    // ---------------------------------------------------------------

    private record ResolvedUser(User user, OtpChannel channel) {}

    private Optional<ResolvedUser> resolveIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) return Optional.empty();
        String trimmed = identifier.trim();

        Optional<User> byUsername = userRepository.findByUsername(trimmed);
        if (byUsername.isPresent()) return Optional.of(new ResolvedUser(byUsername.get(), OtpChannel.EMAIL));

        Optional<User> byEmail = userRepository.findByEmail(trimmed);
        if (byEmail.isPresent()) return Optional.of(new ResolvedUser(byEmail.get(), OtpChannel.EMAIL));

        Optional<User> byPhone = userRepository.findByPhone(trimmed);
        if (byPhone.isPresent()) return Optional.of(new ResolvedUser(byPhone.get(), OtpChannel.SMS));

        return Optional.empty();
    }

    /**
     * Issues + sends an OTP by SMS/email. The code is never returned or logged.
     * Silently does nothing if a code for this user + purpose was issued within the resend cooldown
     * (so a cooldown can't be used to tell whether an account exists).
     */
    private void sendOtp(ResolvedUser resolved, TokenType type) {
        if (emailTokenService.isInResendCooldown(resolved.user(), type)) {
            return;
        }
        String otp = emailTokenService.issue(resolved.user(), type);
        boolean useSms = resolved.channel() == OtpChannel.SMS && resolved.user().getPhone() != null;
        String email = resolved.user().getEmail();

        // SMS first when the person identified themselves by phone. If SMS was not really sent
        // (no provider wired up / disabled), fall back to email so the code still reaches them.
        boolean delivered = false;
        switch (type) {
            case EMAIL_VERIFY -> {
                if (useSms) delivered = smsService.sendVerificationOtp(resolved.user(), otp);
                if (!delivered && email != null) emailService.sendVerificationOtp(resolved.user(), otp);
            }
            case PASSWORD_RESET -> {
                if (useSms) delivered = smsService.sendPasswordResetOtp(resolved.user(), otp);
                if (!delivered && email != null) emailService.sendPasswordResetOtp(resolved.user(), otp);
            }
            case LOGIN_OTP -> {
                if (useSms) delivered = smsService.sendLoginOtp(resolved.user(), otp);
                if (!delivered && email != null) emailService.sendLoginOtp(resolved.user(), otp);
            }
        }
    }

    /**
     * Enforces the login "portal" the person picked on the frontend — a Super Admin account
     * must sign in through the Super Admin portal, and every other account must use the Staff
     * portal. A null/blank portal (older callers) skips the check entirely.
     */
    private void assertPortalMatches(User user, String portal) {
        if (portal == null || portal.isBlank()) return;
        boolean isSuperAdminAccount = user.getRole().getRoleName() == RoleName.SUPER_ADMIN;
        if ("SUPER_ADMIN".equalsIgnoreCase(portal) && !isSuperAdminAccount) {
            throw new UnauthorizedException("This is not a Super Admin account. Please use the Staff login instead");
        }
        if ("STAFF".equalsIgnoreCase(portal) && isSuperAdminAccount) {
            throw new UnauthorizedException("Super Admin accounts must sign in through the Super Admin login");
        }
    }

    // ---------------------------------------------------------------
    // Password login
    // ---------------------------------------------------------------

    @Override
    @Transactional(noRollbackFor = UnauthorizedException.class) // keep the LOGIN_FAILED audit row
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        User user = resolveIdentifier(request.getUsername())
                .map(ResolvedUser::user)
                .orElseThrow(() -> new UnauthorizedException("Invalid username, email, phone, or password"));

        // Only reveal the account's status once the caller has proved they know the password,
        // so nobody can probe which identifiers exist / are pending / are disabled.
        if (user.getStatus() != UserStatus.ACTIVE) {
            if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                throw new UnauthorizedException("Invalid username, email, phone, or password");
            }
            if (!user.isEmailVerified()) {
                throw new UnauthorizedException(NOT_VERIFIED_MESSAGE);
            }
            if (user.getStatus() == UserStatus.PENDING) {
                throw new UnauthorizedException("Your account is awaiting admin approval");
            }
            if (user.getStatus() == UserStatus.REJECTED) {
                throw new UnauthorizedException("Your registration was rejected. Contact an administrator");
            }
            throw new UnauthorizedException("Your account has been disabled. Contact an administrator");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getUsername(), request.getPassword())
            );
        } catch (BadCredentialsException ex) {
            auditLogService.log(user, AuditAction.LOGIN_FAILED, "users", String.valueOf(user.getUserId()),
                    "Failed login attempt", clientIp(httpRequest));
            throw new UnauthorizedException("Invalid username, email, phone, or password");
        }

        if (!user.isEmailVerified()) {
            throw new UnauthorizedException(NOT_VERIFIED_MESSAGE);
        }
        assertPortalMatches(user, request.getPortal());

        return issueSession(user, httpRequest, "User logged in (password)");
    }

    // ---------------------------------------------------------------
    // Passwordless OTP login
    // ---------------------------------------------------------------

    @Override
    @Transactional
    public void requestLoginOtp(String identifier, String portal) {
        // Deliberately silent when the identifier is unknown/inactive/wrong-portal (no account enumeration).
        resolveIdentifier(identifier).ifPresent(resolved -> {
            User user = resolved.user();
            boolean portalOk = portal == null || portal.isBlank()
                    || ("SUPER_ADMIN".equalsIgnoreCase(portal) == (user.getRole().getRoleName() == RoleName.SUPER_ADMIN));
            if (portalOk && user.getStatus() == UserStatus.ACTIVE && user.isEmailVerified()) {
                sendOtp(resolved, TokenType.LOGIN_OTP);
            }
        });
    }

    @Override
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResponse verifyLoginOtp(String identifier, String otp, String portal, HttpServletRequest httpRequest) {
        User user = resolveIdentifier(identifier)
                .map(ResolvedUser::user)
                .orElseThrow(() -> new UnauthorizedException(EmailTokenService.MSG_INVALID));

        if (user.getStatus() != UserStatus.ACTIVE || !user.isEmailVerified()) {
            throw new UnauthorizedException("Your account is not active. Please contact an administrator");
        }

        try {
            emailTokenService.consume(user, otp, TokenType.LOGIN_OTP);
        } catch (BadRequestException ex) {
            auditLogService.log(user, AuditAction.LOGIN_FAILED, "users", String.valueOf(user.getUserId()),
                    "Failed OTP login attempt", clientIp(httpRequest));
            throw new UnauthorizedException(ex.getMessage());
        }
        assertPortalMatches(user, portal);

        return issueSession(user, httpRequest, "User logged in (OTP)");
    }

    private AuthResponse issueSession(User user, HttpServletRequest httpRequest, String auditDescription) {
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        UserPrincipal principal = new UserPrincipal(user);
        String token = jwtService.generateToken(principal);
        String refreshToken = refreshTokenService.createRefreshToken(user).getToken();

        auditLogService.log(user, AuditAction.LOGIN, "users", String.valueOf(user.getUserId()),
                auditDescription, clientIp(httpRequest));

        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .userId(user.getUserId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole().getRoleName().name())
                .build();
    }

    // ---------------------------------------------------------------
    // Registration
    // ---------------------------------------------------------------

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        // Username is chosen by the user and stored exactly as entered (DTO validation already checked the format).
        String username = request.getUsername();
        if (userRepository.existsByUsername(username)) {
            throw new BadRequestException(USERNAME_TAKEN_MESSAGE);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already registered");
        }
        if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone().trim())) {
            throw new BadRequestException("Phone number already registered");
        }

        RoleName roleName = RoleName.valueOf(request.getRole());
        Role role = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new BadRequestException("Role not configured: " + roleName));

        User user = User.builder()
                .username(username)
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .role(role)
                .status(UserStatus.PENDING) // requires admin approval
                .build();
        user = userRepository.save(user);

        if (roleName == RoleName.DOCTOR) {
            long count = doctorRepository.count();
            Doctor doctor = Doctor.builder()
                    .user(user)
                    .doctorCode(CodeGenerator.doctorCode(count))
                    .specialization(request.getSpecialization())
                    .qualification(request.getQualification())
                    .licenseNumber(request.getLicenseNumber())
                    .phone(request.getPhone())
                    .status(ActiveStatus.ACTIVE)
                    .build();
            doctorRepository.save(doctor);
        } else if (roleName == RoleName.NURSE) {
            long count = nurseRepository.count();
            Nurse nurse = Nurse.builder()
                    .user(user)
                    .nurseCode(CodeGenerator.nurseCode(count))
                    .qualification(request.getNurseQualification())
                    .phone(request.getPhone())
                    .status(ActiveStatus.ACTIVE)
                    .build();
            nurseRepository.save(nurse);
        }

        auditLogService.log(user, AuditAction.CREATE, "users", String.valueOf(user.getUserId()),
                "Self-registration (pending OTP verification and admin approval)", null);

        sendOtp(new ResolvedUser(user, OtpChannel.EMAIL), TokenType.EMAIL_VERIFY);
    }

    // ---------------------------------------------------------------
    // OTP verification / resend
    // ---------------------------------------------------------------

    @Override
    @Transactional
    public String verifyOtp(String identifier, String otp) {
        User user = resolveIdentifier(identifier)
                .map(ResolvedUser::user)
                .orElseThrow(() -> new BadRequestException(EmailTokenService.MSG_INVALID));
        emailTokenService.consume(user, otp, TokenType.EMAIL_VERIFY);
        user.setEmailVerified(true);
        userRepository.save(user);
        auditLogService.log(user, AuditAction.EMAIL_VERIFIED, "users", String.valueOf(user.getUserId()),
                "Account verified via OTP", null);
        // Verifying the email never approves the account: PENDING stays PENDING until an admin approves it.
        return user.getStatus() == UserStatus.PENDING
                ? "Email verified successfully. Your account is now waiting for admin approval."
                : "Email verified successfully. You can log in now.";
    }

    @Override
    @Transactional
    public void resendOtp(String identifier) {
        // Deliberately silent when the identifier is unknown/already verified (no account enumeration).
        resolveIdentifier(identifier).ifPresent(resolved -> {
            User user = resolved.user();
            if (!user.isEmailVerified()
                    && user.getStatus() != UserStatus.REJECTED
                    && user.getStatus() != UserStatus.DISABLED) {
                sendOtp(resolved, TokenType.EMAIL_VERIFY); // issuing a new code deletes the previous one
            }
        });
    }

    // ---------------------------------------------------------------
    // Password reset
    // ---------------------------------------------------------------

    @Override
    @Transactional
    public void forgotPassword(String identifier) {
        resolveIdentifier(identifier).ifPresent(resolved -> {
            User user = resolved.user();
            if (user.getStatus() != UserStatus.REJECTED && user.getStatus() != UserStatus.DISABLED) {
                sendOtp(resolved, TokenType.PASSWORD_RESET);
            }
        });
    }

    @Override
    @Transactional
    public void resetPassword(String identifier, String otp, String newPassword) {
        User user = resolveIdentifier(identifier)
                .map(ResolvedUser::user)
                .orElseThrow(() -> new BadRequestException(EmailTokenService.MSG_INVALID));
        emailTokenService.consume(user, otp, TokenType.PASSWORD_RESET);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setEmailVerified(true); // they just proved they own this identifier
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getUserId()); // sign out every existing session
        auditLogService.log(user, AuditAction.PASSWORD_RESET, "users", String.valueOf(user.getUserId()),
                "Password reset via OTP", null);
    }

    // ---------------------------------------------------------------
    // Refresh / logout
    // ---------------------------------------------------------------

    private String clientIp(HttpServletRequest request) {
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null ? forwarded.split(",")[0] : request.getRemoteAddr();
    }

    @Override
    @Transactional
    public AuthResponse refresh(String refreshTokenValue) {
        com.meditrack.entity.RefreshToken storedToken = refreshTokenService.findByToken(refreshTokenValue);
        storedToken = refreshTokenService.verifyExpiration(storedToken);

        User user = storedToken.getUser();
        if (user.getStatus() != UserStatus.ACTIVE || !user.isEmailVerified()) {
            refreshTokenService.revokeAllForUser(user.getUserId());
            throw new TokenRefreshException("Your account is not active. Please contact an administrator");
        }
        UserPrincipal principal = new UserPrincipal(user);
        String newAccessToken = jwtService.generateToken(principal);
        // rotate the refresh token so a leaked/used one can't be replayed indefinitely
        String newRefreshToken = refreshTokenService.createRefreshToken(user).getToken();

        return AuthResponse.builder()
                .token(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .userId(user.getUserId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole().getRoleName().name())
                .build();
    }

    @Override
    @Transactional
    public void logout(Integer userId) {
        refreshTokenService.revokeAllForUser(userId);
        User user = userRepository.findById(userId).orElse(null);
        auditLogService.log(user, AuditAction.LOGOUT, "users", String.valueOf(userId), "User logged out", null);
    }
}
