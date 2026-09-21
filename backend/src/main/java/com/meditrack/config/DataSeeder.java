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
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Seeds the three roles (ADMIN / DOCTOR / NURSE) and one demo account per role
 * on first boot, purely so the API is usable immediately after `mvn spring-boot:run`.
 * Safe to delete this class (or comment out the @Component) once you seed
 * your own data via the MySQL script instead.
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

        if (userRepository.count() > 0) {
            log.info("Users already exist — skipping demo data seed.");
            return;
        }

        Role adminRole = roleRepository.findByRoleName(RoleName.ADMIN).orElseThrow();
        Role doctorRole = roleRepository.findByRoleName(RoleName.DOCTOR).orElseThrow();
        Role nurseRole = roleRepository.findByRoleName(RoleName.NURSE).orElseThrow();

        User admin = userRepository.save(User.builder()
                .username("admin").email("admin@meditrack.local")
                .passwordHash(passwordEncoder.encode("Password@123"))
                .firstName("System").lastName("Admin")
                .role(adminRole).status(UserStatus.ACTIVE).emailVerified(true)
                .build());

        User doctorUser = userRepository.save(User.builder()
                .username("drrahul").email("drrahul@meditrack.local")
                .passwordHash(passwordEncoder.encode("Password@123"))
                .firstName("Rahul").lastName("Sharma")
                .role(doctorRole).status(UserStatus.ACTIVE).emailVerified(true)
                .build());
        Doctor doctor = doctorRepository.save(Doctor.builder()
                .user(doctorUser)
                .doctorCode(CodeGenerator.doctorCode(doctorRepository.count()))
                .specialization("General Medicine")
                .qualification("MBBS, MD")
                .status(ActiveStatus.ACTIVE)
                .build());

        User nurseUser = userRepository.save(User.builder()
                .username("nursepriya").email("nursepriya@meditrack.local")
                .passwordHash(passwordEncoder.encode("Password@123"))
                .firstName("Priya").lastName("Nair")
                .role(nurseRole).status(UserStatus.ACTIVE).emailVerified(true)
                .build());
        Nurse nurse = nurseRepository.save(Nurse.builder()
                .user(nurseUser)
                .nurseCode(CodeGenerator.nurseCode(nurseRepository.count()))
                .qualification("B.Sc Nursing")
                .status(ActiveStatus.ACTIVE)
                .build());

        // One demo patient assigned to the demo doctor + nurse, so their dashboards are not empty.
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

        log.info("Seeded demo users: admin/Password@123, drrahul/Password@123, nursepriya/Password@123");
    }
}
