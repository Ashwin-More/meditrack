package com.meditrack.config;

import com.meditrack.entity.*;
import com.meditrack.enums.ActiveStatus;
import com.meditrack.enums.AppointmentStatus;
import com.meditrack.enums.RoleName;
import com.meditrack.enums.UserStatus;
import com.meditrack.repository.*;
import com.meditrack.util.CodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Makes sure the roles and the four demo accounts (superadmin / admin / drrahul / nursepriya) exist.
 *
 * Each account is checked on its own on EVERY boot, so a database that already had users from an
 * older version (before the SUPER_ADMIN role existed) still gets its missing superadmin. Existing
 * accounts are never overwritten, except:
 *   - app.seed.email-base set  -> demo accounts still on a "*.local" address are moved to a real,
 *     deliverable inbox (plus-addressing: name+superadmin@gmail.com) so OTP emails actually arrive;
 *   - app.seed.reset-demo-passwords=true -> demo passwords are reset to the documented defaults and
 *     the account is re-activated/verified (recovery switch; turn it off again afterwards).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final NurseRepository nurseRepository;
    private final PasswordEncoder passwordEncoder;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final VitalRepository vitalRepository;

    /** e.g. you@gmail.com -> demo accounts get you+superadmin@gmail.com, you+admin@gmail.com, ... */
    @Value("${app.seed.email-base:}")
    private String emailBase;

    @Value("${app.seed.reset-demo-passwords:false}")
    private boolean resetDemoPasswords;

    @Override
    @Transactional
    public void run(String... args) {
        for (RoleName roleName : RoleName.values()) {
            roleRepository.findByRoleName(roleName).orElseGet(() ->
                    roleRepository.save(Role.builder()
                            .roleName(roleName)
                            .description(roleName.name() + " role")
                            .build()));
        }

        User superAdmin = ensureUser("superadmin", "SuperAdmin@123", "Institute", "Owner", "+919000000000", RoleName.SUPER_ADMIN);
        User admin = ensureUser("admin", "Password@123", "System", "Admin", "+919000000001", RoleName.ADMIN);
        User doctorUser = ensureUser("drrahul", "Password@123", "Rahul", "Sharma", "+919000000002", RoleName.DOCTOR);
        User nurseUser = ensureUser("nursepriya", "Password@123", "Priya", "Nair", "+919000000003", RoleName.NURSE);

        Doctor doctor = null;
        if (doctorUser != null) {
            doctor = doctorRepository.findByUser_UserId(doctorUser.getUserId()).orElseGet(() ->
                    doctorRepository.save(Doctor.builder()
                            .user(doctorUser)
                            .doctorCode(CodeGenerator.doctorCode(doctorRepository.count()))
                            .specialization("General Medicine")
                            .qualification("MBBS, MD")
                            .status(ActiveStatus.ACTIVE)
                            .build()));
        }
        Nurse nurse = null;
        if (nurseUser != null) {
            nurse = nurseRepository.findByUser_UserId(nurseUser.getUserId()).orElseGet(() ->
                    nurseRepository.save(Nurse.builder()
                            .user(nurseUser)
                            .nurseCode(CodeGenerator.nurseCode(nurseRepository.count()))
                            .qualification("B.Sc Nursing")
                            .status(ActiveStatus.ACTIVE)
                            .build()));
        }

        // One demo patient (only on a completely empty patient table) so dashboards are not empty.
        if (doctor != null && nurse != null && patientRepository.count() == 0) {
            Patient demo = patientRepository.save(Patient.builder()
                    .patientCode(CodeGenerator.patientCode(0))
                    .firstName("Aarav").lastName("Mehta")
                    .dateOfBirth(LocalDate.of(1988, 4, 12)).gender("MALE").bloodGroup("O+")
                    .phone("9876543210")
                    .assignedDoctor(doctor).assignedNurse(nurse)
                    .build());
            appointmentRepository.save(Appointment.builder()
                    .patient(demo).doctor(doctor)
                    .scheduledAt(LocalDateTime.now().plusDays(1).withHour(10).withMinute(30).withSecond(0).withNano(0))
                    .reason("Routine check-up").status(AppointmentStatus.SCHEDULED)
                    .build());
            vitalRepository.save(Vital.builder()
                    .patient(demo).recordedBy(nurseUser)
                    .temperatureC(new BigDecimal("36.8")).pulseBpm(76)
                    .systolicBp(120).diastolicBp(80).respiratoryRate(16).spo2(98)
                    .weightKg(new BigDecimal("72.5")).notes("Baseline vitals on registration")
                    .build());
        }

        log.info("Demo accounts ready: superadmin/SuperAdmin@123 (Super Admin portal), "
                + "admin/Password@123, drrahul/Password@123, nursepriya/Password@123 (Staff portal). "
                + "Demo email addresses: {}", hasRealEmailBase()
                ? "name+<username>@" + emailBase.substring(emailBase.indexOf('@') + 1) + " (real inbox)"
                : "<username>@meditrack.local (undeliverable - set SEED_EMAIL_BASE to receive OTP emails)");
    }

    private boolean hasRealEmailBase() {
        return emailBase != null && emailBase.trim().matches("^[^@\\s+]+@[^@\\s]+\\.[^@\\s]+$");
    }

    private String demoEmail(String username) {
        if (hasRealEmailBase()) {
            String base = emailBase.trim();
            int at = base.indexOf('@');
            return base.substring(0, at) + "+" + username + base.substring(at);
        }
        return username + "@meditrack.local";
    }

    /** Creates the account if missing, otherwise only applies the two opt-in repairs described above. */
    private User ensureUser(String username, String password, String first, String last, String phone, RoleName roleName) {
        Role role = roleRepository.findByRoleName(roleName).orElseThrow();
        String email = demoEmail(username);

        User existing = userRepository.findByUsername(username).orElse(null);
        if (existing == null) {
            if (userRepository.existsByEmail(email)) {
                log.warn("Cannot create demo account '{}': email {} is already used by another user.", username, email);
                return null;
            }
            boolean phoneFree = !userRepository.existsByPhone(phone);
            User created = userRepository.save(User.builder()
                    .username(username).email(email).phone(phoneFree ? phone : null)
                    .passwordHash(passwordEncoder.encode(password))
                    .firstName(first).lastName(last)
                    .role(role).status(UserStatus.ACTIVE).emailVerified(true)
                    .build());
            log.info("Created missing demo account '{}' ({})", username, roleName);
            return created;
        }

        boolean changed = false;
        if (hasRealEmailBase() && existing.getEmail() != null
                && existing.getEmail().toLowerCase().endsWith(".local")
                && !userRepository.existsByEmail(email)) {
            existing.setEmail(email);
            changed = true;
            log.info("Demo account '{}' email changed to {} so OTP emails can be delivered", username, email);
        }
        if (resetDemoPasswords) {
            existing.setPasswordHash(passwordEncoder.encode(password));
            existing.setStatus(UserStatus.ACTIVE);
            existing.setEmailVerified(true);
            if (existing.getRole() == null || existing.getRole().getRoleName() != roleName) {
                existing.setRole(role);
            }
            changed = true;
            log.warn("Demo account '{}' password reset to default (app.seed.reset-demo-passwords=true)", username);
        }
        return changed ? userRepository.save(existing) : existing;
    }
}
