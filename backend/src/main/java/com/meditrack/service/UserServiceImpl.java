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
import com.meditrack.util.CodeGenerator;
import lombok.RequiredArgsConstructor;
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

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
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
                "User created by admin (email verification pending)", null);

        emailService.sendVerificationEmail(user, emailTokenService.issue(user, TokenType.EMAIL_VERIFY));

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
        user.setStatus(UserStatus.REJECTED);
        userRepository.save(user);
        auditLogService.log(user, AuditAction.REJECT, "users", String.valueOf(userId), "Account rejected", null);
        return toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse disableUser(Integer userId) {
        User user = findUser(userId);
        user.setStatus(UserStatus.DISABLED);
        userRepository.save(user);
        auditLogService.log(user, AuditAction.UPDATE, "users", String.valueOf(userId), "Account disabled", null);
        return toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse enableUser(Integer userId) {
        User user = findUser(userId);
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
        user.setFirstName(firstName.trim());
        user.setLastName(lastName.trim());
        user.setPhone(phone == null || phone.isBlank() ? null : phone.trim());
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
        if (user.isEmailVerified()) {
            throw new BadRequestException("This user's email is already verified");
        }
        emailService.sendVerificationEmail(user, emailTokenService.issue(user, TokenType.EMAIL_VERIFY));
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
