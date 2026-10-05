package com.meditrack.service;

import com.meditrack.dto.CreateUserRequest;
import com.meditrack.dto.UserResponse;
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
import com.meditrack.exception.ResourceNotFoundException;
import com.meditrack.repository.DoctorRepository;
import com.meditrack.repository.NurseRepository;
import com.meditrack.repository.RoleRepository;
import com.meditrack.repository.UserRepository;
import com.meditrack.security.AccessControlService;
import com.meditrack.util.CodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DoctorRepository doctorRepository;
    private final NurseRepository nurseRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final EmailTokenService emailTokenService;
    private final EmailService emailService;
    private final RefreshTokenService refreshTokenService;
    private final AccessControlService accessControl;

    /** ADMIN and SUPER_ADMIN are both "admin-tier" — only a SUPER_ADMIN may create/approve/reject/disable/enable one. */
    private static boolean isAdminTier(RoleName role) {
        return role == RoleName.ADMIN || role == RoleName.SUPER_ADMIN;
    }

    private boolean actingUserIsSuperAdmin() {
        return "SUPER_ADMIN".equals(accessControl.currentPrincipal().getRole());
    }

    private void assertCanManageAdminTierAccount(User target, String action) {
        if (isAdminTier(target.getRole().getRoleName()) && !actingUserIsSuperAdmin()) {
            throw new AccessDeniedException("Only a Super Admin can " + action + " an Admin-level account");
        }
    }

    private void assertNotActingOnSelf(User target, String action) {
        Integer actingUserId = accessControl.currentPrincipal().getUserId();
        if (target.getUserId().equals(actingUserId)) {
            throw new BadRequestException("You cannot " + action + " your own account");
        }
    }

    /** Refuses to disable/reject the very last active Super Admin, so the system can never lock itself out. */
    private void assertNotLastActiveSuperAdmin(User target) {
        if (target.getRole().getRoleName() == RoleName.SUPER_ADMIN
                && target.getStatus() == UserStatus.ACTIVE
                && userRepository.countByRole_RoleNameAndStatus(RoleName.SUPER_ADMIN, UserStatus.ACTIVE) <= 1) {
            throw new BadRequestException("Cannot disable or reject the last remaining Super Admin account");
        }
    }

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        String username = CodeGenerator.username(request.getRole(), userRepository.count(), userRepository::existsByUsername);
        if (username == null) {
            username = request.getUsername();
            if (username == null || username.isBlank()) throw new BadRequestException("Username is required");
            if (userRepository.existsByUsername(username)) {
                throw new BadRequestException(AuthServiceImpl.USERNAME_TAKEN_MESSAGE);
            }
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already registered");
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()
                && userRepository.existsByPhone(request.getPhone().trim())) {
            throw new BadRequestException("Phone number already registered");
        }

        RoleName roleName = RoleName.valueOf(request.getRole());
        if (isAdminTier(roleName) && !actingUserIsSuperAdmin()) {
            throw new AccessDeniedException("Only a Super Admin can create an Admin-level account");
        }
        Role role = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new BadRequestException("Role not configured: " + roleName));

        User user = User.builder()
                .username(username)
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .role(role)
                .status(UserStatus.ACTIVE) // admin-created accounts skip the approval queue...
                .emailVerified(false)      // ...but the owner must still verify their email before first login
                .build();
        user = userRepository.save(user);

        if (roleName == RoleName.DOCTOR) {
            Doctor doctor = Doctor.builder()
                    .user(user)
                    .doctorCode(CodeGenerator.doctorCode(doctorRepository.count()))
                    .specialization(request.getSpecialization())
                    .qualification(request.getQualification())
                    .licenseNumber(request.getLicenseNumber())
                    .phone(request.getPhone())
                    .status(ActiveStatus.ACTIVE)
                    .build();
            doctorRepository.save(doctor);
        } else if (roleName == RoleName.NURSE) {
            Nurse nurse = Nurse.builder()
                    .user(user)
                    .nurseCode(CodeGenerator.nurseCode(nurseRepository.count()))
                    .qualification(request.getQualification())
                    .phone(request.getPhone())
                    .status(ActiveStatus.ACTIVE)
                    .build();
            nurseRepository.save(nurse);
        }

        auditLogService.log(user, AuditAction.CREATE, "users", String.valueOf(user.getUserId()),
                "User created by admin (OTP verification pending)", null);

        emailService.sendVerificationOtp(user, emailTokenService.issue(user, TokenType.EMAIL_VERIFY));

        return toResponse(user);
    }

    @Override
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<UserResponse> getPendingUsers() {
        return userRepository.findByStatus(UserStatus.PENDING).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public UserResponse approveUser(Integer userId) {
        User user = findUser(userId);
        assertCanManageAdminTierAccount(user, "approve");
        if (!user.isEmailVerified()) {
            throw new BadRequestException("This user has not verified their email address yet");
        }
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        auditLogService.log(user, AuditAction.APPROVE, "users", String.valueOf(userId), "Account approved", null);
        return toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse rejectUser(Integer userId) {
        User user = findUser(userId);
        assertCanManageAdminTierAccount(user, "reject");
        assertNotActingOnSelf(user, "reject");
        assertNotLastActiveSuperAdmin(user);
        user.setStatus(UserStatus.REJECTED);
        userRepository.save(user);
        auditLogService.log(user, AuditAction.REJECT, "users", String.valueOf(userId), "Account rejected", null);
        return toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse disableUser(Integer userId) {
        User user = findUser(userId);
        assertCanManageAdminTierAccount(user, "disable");
        assertNotActingOnSelf(user, "disable");
        assertNotLastActiveSuperAdmin(user);
        user.setStatus(UserStatus.DISABLED);
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getUserId()); // immediately sign the disabled account out everywhere
        auditLogService.log(user, AuditAction.UPDATE, "users", String.valueOf(userId), "Account disabled", null);
        return toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse enableUser(Integer userId) {
        User user = findUser(userId);
        assertCanManageAdminTierAccount(user, "enable");
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        auditLogService.log(user, AuditAction.UPDATE, "users", String.valueOf(userId), "Account enabled", null);
        return toResponse(user);
    }

    @Override
    public UserResponse getCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateProfile(String username, String firstName, String lastName, String phone) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String normalizedPhone = phone == null || phone.isBlank() ? null : phone.trim();
        if (normalizedPhone != null) {
            userRepository.findByPhone(normalizedPhone).ifPresent(existing -> {
                if (!existing.getUserId().equals(user.getUserId())) {
                    throw new BadRequestException("Phone number already registered to another account");
                }
            });
        }
        user.setFirstName(firstName.trim());
        user.setLastName(lastName.trim());
        user.setPhone(normalizedPhone);
        userRepository.save(user);
        auditLogService.log(user, AuditAction.UPDATE, "users", String.valueOf(user.getUserId()), "Profile updated", null);
        return toResponse(user);
    }

    @Override
    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BadRequestException("New password must be different from the current one");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getUserId()); // other devices must log in again
        auditLogService.log(user, AuditAction.PASSWORD_RESET, "users", String.valueOf(user.getUserId()),
                "Password changed by the user", null);
    }

    @Override
    @Transactional
    public void resendVerification(Integer userId) {
        User user = findUser(userId);
        assertCanManageAdminTierAccount(user, "resend a verification code for");
        if (user.isEmailVerified()) {
            throw new BadRequestException("This user's account is already verified");
        }
        emailService.sendVerificationOtp(user, emailTokenService.issue(user, TokenType.EMAIL_VERIFY));
    }

    private User findUser(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .role(user.getRole().getRoleName().name())
                .status(user.getStatus().name())
                .emailVerified(user.isEmailVerified())
                .createdAt(user.getCreatedAt())
                .lastLogin(user.getLastLogin())
                .build();
    }
}
