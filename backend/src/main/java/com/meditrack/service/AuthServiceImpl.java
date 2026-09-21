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

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** The frontend looks for the phrase 'verify your email' to offer a 'resend link' button. */
    static final String EMAIL_NOT_VERIFIED_MESSAGE =
            "Please verify your email address before logging in. Check your inbox, or request a new verification link";

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

    @Override
    @Transactional(noRollbackFor = UnauthorizedException.class) // keep the LOGIN_FAILED audit row
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));

        // Only reveal the account's status once the caller has proved they know the password,
        // so nobody can probe which usernames exist / are pending / are disabled.
        if (user.getStatus() != UserStatus.ACTIVE) {
            if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                throw new UnauthorizedException("Invalid username or password");
            }
            if (!user.isEmailVerified()) {
                throw new UnauthorizedException(EMAIL_NOT_VERIFIED_MESSAGE);
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
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (BadCredentialsException ex) {
            auditLogService.log(user, AuditAction.LOGIN_FAILED, "users", String.valueOf(user.getUserId()),
                    "Failed login attempt", clientIp(httpRequest));
            throw new UnauthorizedException("Invalid username or password");
        }

        if (!user.isEmailVerified()) {
            throw new UnauthorizedException(EMAIL_NOT_VERIFIED_MESSAGE);
        }

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        UserPrincipal principal = new UserPrincipal(user);
        String token = jwtService.generateToken(principal);
        String refreshToken = refreshTokenService.createRefreshToken(user).getToken();

        auditLogService.log(user, AuditAction.LOGIN, "users", String.valueOf(user.getUserId()),
                "User logged in", clientIp(httpRequest));

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

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already registered");
        }

        RoleName roleName = RoleName.valueOf(request.getRole());
        Role role = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new BadRequestException("Role not configured: " + roleName));

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
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
                "Self-registration (pending email verification and admin approval)", null);

        emailService.sendVerificationEmail(user, emailTokenService.issue(user, TokenType.EMAIL_VERIFY));
    }

    @Override
    @Transactional
    public String verifyEmail(String token) {
        User user = emailTokenService.consume(token, TokenType.EMAIL_VERIFY);
        user.setEmailVerified(true);
        userRepository.save(user);
        auditLogService.log(user, AuditAction.EMAIL_VERIFIED, "users", String.valueOf(user.getUserId()),
                "Email address verified", null);
        return user.getStatus() == UserStatus.PENDING
                ? "Email verified. Your account is now waiting for admin approval."
                : "Email verified. You can log in now.";
    }

    @Override
    @Transactional
    public void resendVerification(String email) {
        // Deliberately silent when the address is unknown / already verified (no account enumeration).
        userRepository.findByEmail(email.trim()).ifPresent(user -> {
            if (!user.isEmailVerified()
                    && user.getStatus() != UserStatus.REJECTED
                    && user.getStatus() != UserStatus.DISABLED) {
                emailService.sendVerificationEmail(user, emailTokenService.issue(user, TokenType.EMAIL_VERIFY));
            }
        });
    }

    @Override
    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmail(email.trim()).ifPresent(user -> {
            if (user.getStatus() != UserStatus.REJECTED && user.getStatus() != UserStatus.DISABLED) {
                emailService.sendPasswordResetEmail(user, emailTokenService.issue(user, TokenType.PASSWORD_RESET));
            }
        });
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        User user = emailTokenService.consume(token, TokenType.PASSWORD_RESET);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setEmailVerified(true); // they just proved they own the inbox
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getUserId()); // sign out every existing session
        auditLogService.log(user, AuditAction.PASSWORD_RESET, "users", String.valueOf(user.getUserId()),
                "Password reset via email link", null);
    }

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
