-- =====================================================================
-- MediTrack — Patient Management & EHR System
-- MySQL 8.x schema (matches the JPA/Hibernate entities 1:1)
--
-- Usage:
--   mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS meditrack
--     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
--   mysql -u root -p meditrack < schema.sql
--
-- Note: if you let Hibernate manage the schema (app.jpa.hibernate.ddl-auto
-- = update/create), you do NOT need to run this file — Hibernate will
-- generate an equivalent schema from the entities on boot. This file is
-- provided so you have an explicit, reviewable DDL for submission /
-- production, and so you can seed data independently of the app.
-- =====================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------
-- 1. roles
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `roles`;
CREATE TABLE `roles` (
    `role_id`     INT AUTO_INCREMENT PRIMARY KEY,
    `role_name`   VARCHAR(30)  NOT NULL,
    `description` VARCHAR(255) NULL,
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT `uq_roles_role_name` UNIQUE (`role_name`),
    CONSTRAINT `chk_roles_role_name` CHECK (`role_name` IN ('ADMIN','DOCTOR','NURSE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 2. users
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
    `user_id`       INT AUTO_INCREMENT PRIMARY KEY,
    `role_id`       INT NOT NULL,
    `username`      VARCHAR(50)  NOT NULL,
    `email`         VARCHAR(120) NOT NULL,
    `password_hash` VARCHAR(255) NOT NULL,
    `first_name`    VARCHAR(50)  NOT NULL,
    `last_name`     VARCHAR(50)  NOT NULL,
    `phone`         VARCHAR(20)  NULL,
    `status`        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `last_login`    DATETIME     NULL,
    CONSTRAINT `uq_users_username` UNIQUE (`username`),
    CONSTRAINT `uq_users_email` UNIQUE (`email`),
    CONSTRAINT `fk_users_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`role_id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_users_status` CHECK (`status` IN ('ACTIVE','PENDING','DISABLED','REJECTED')),
    INDEX `idx_users_role_id` (`role_id`),
    INDEX `idx_users_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 3. doctors
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `doctors`;
CREATE TABLE `doctors` (
    `doctor_id`      INT AUTO_INCREMENT PRIMARY KEY,
    `user_id`        INT NOT NULL,
    `doctor_code`    VARCHAR(20)  NOT NULL,
    `specialization` VARCHAR(100) NULL,
    `qualification`  VARCHAR(150) NULL,
    `license_number` VARCHAR(50)  NULL,
    `phone`          VARCHAR(20)  NULL,
    `address`        VARCHAR(255) NULL,
    `joining_date`   DATE         NULL,
    `status`         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT `uq_doctors_user_id` UNIQUE (`user_id`),
    CONSTRAINT `uq_doctors_doctor_code` UNIQUE (`doctor_code`),
    CONSTRAINT `fk_doctors_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
    CONSTRAINT `chk_doctors_status` CHECK (`status` IN ('ACTIVE','INACTIVE')),
    INDEX `idx_doctors_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 4. nurses
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `nurses`;
CREATE TABLE `nurses` (
    `nurse_id`      INT AUTO_INCREMENT PRIMARY KEY,
    `user_id`       INT NOT NULL,
    `nurse_code`    VARCHAR(20)  NOT NULL,
    `qualification` VARCHAR(150) NULL,
    `phone`         VARCHAR(20)  NULL,
    `joining_date`  DATE         NULL,
    `status`        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT `uq_nurses_user_id` UNIQUE (`user_id`),
    CONSTRAINT `uq_nurses_nurse_code` UNIQUE (`nurse_code`),
    CONSTRAINT `fk_nurses_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
    CONSTRAINT `chk_nurses_status` CHECK (`status` IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 5. patients
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `patients`;
CREATE TABLE `patients` (
    `patient_id`               INT AUTO_INCREMENT PRIMARY KEY,
    `patient_code`             VARCHAR(30)  NOT NULL,           -- e.g. PAT-2026-001
    `first_name`               VARCHAR(50)  NOT NULL,
    `last_name`                VARCHAR(50)  NOT NULL,
    `date_of_birth`            DATE         NULL,
    `gender`                   VARCHAR(10)  NULL,
    `blood_group`              VARCHAR(5)   NULL,
    `phone`                    VARCHAR(20)  NULL,
    `email`                    VARCHAR(120) NULL,
    `address`                  VARCHAR(255) NULL,
    `emergency_contact_name`   VARCHAR(100) NULL,
    `emergency_contact_phone`  VARCHAR(20)  NULL,
    `status`                   VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    `created_at`               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT `uq_patients_patient_code` UNIQUE (`patient_code`),
    CONSTRAINT `chk_patients_status` CHECK (`status` IN ('ACTIVE','INACTIVE')),
    INDEX `idx_patients_last_name` (`last_name`),
    INDEX `idx_patients_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 6. patient_allergies
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `patient_allergies`;
CREATE TABLE `patient_allergies` (
    `allergy_id`    INT AUTO_INCREMENT PRIMARY KEY,
    `patient_id`    INT NOT NULL,
    `allergy_name`  VARCHAR(100) NOT NULL,
    `reaction`      VARCHAR(150) NULL,
    `severity`      VARCHAR(20)  NULL,
    `notes`         VARCHAR(255) NULL,
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT `fk_allergies_patient` FOREIGN KEY (`patient_id`) REFERENCES `patients` (`patient_id`) ON DELETE CASCADE,
    CONSTRAINT `chk_allergies_severity` CHECK (`severity` IS NULL OR `severity` IN ('MILD','MODERATE','SEVERE')),
    INDEX `idx_allergies_patient_id` (`patient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 7. patient_conditions
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `patient_conditions`;
CREATE TABLE `patient_conditions` (
    `condition_id`    INT AUTO_INCREMENT PRIMARY KEY,
    `patient_id`      INT NOT NULL,
    `condition_name`  VARCHAR(100) NOT NULL,
    `diagnosed_date`  DATE         NULL,
    `status`          VARCHAR(20)  NOT NULL DEFAULT 'ONGOING',
    `notes`           VARCHAR(255) NULL,
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT `fk_conditions_patient` FOREIGN KEY (`patient_id`) REFERENCES `patients` (`patient_id`) ON DELETE CASCADE,
    CONSTRAINT `chk_conditions_status` CHECK (`status` IN ('ONGOING','RESOLVED')),
    INDEX `idx_conditions_patient_id` (`patient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 8. visits
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `visits`;
CREATE TABLE `visits` (
    `visit_id`         INT AUTO_INCREMENT PRIMARY KEY,
    `patient_id`       INT NOT NULL,
    `doctor_id`        INT NOT NULL,
    `visit_date`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `visit_type`       VARCHAR(30)  NULL,          -- OPD / FOLLOW_UP / EMERGENCY
    `chief_complaint`  VARCHAR(255) NULL,
    `symptoms`         TEXT         NULL,
    `doctor_notes`     TEXT         NULL,
    `follow_up_date`   DATE         NULL,
    `status`           VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT `fk_visits_patient` FOREIGN KEY (`patient_id`) REFERENCES `patients` (`patient_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_visits_doctor`  FOREIGN KEY (`doctor_id`)  REFERENCES `doctors`  (`doctor_id`)  ON DELETE RESTRICT,
    CONSTRAINT `chk_visits_type` CHECK (`visit_type` IS NULL OR `visit_type` IN ('OPD','FOLLOW_UP','EMERGENCY')),
    CONSTRAINT `chk_visits_status` CHECK (`status` IN ('OPEN','CLOSED')),
    INDEX `idx_visits_patient_id` (`patient_id`),
    INDEX `idx_visits_doctor_id` (`doctor_id`),
    INDEX `idx_visits_visit_date` (`visit_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 9. diagnoses
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `diagnoses`;
CREATE TABLE `diagnoses` (
    `diagnosis_id`    INT AUTO_INCREMENT PRIMARY KEY,
    `visit_id`        INT NOT NULL,
    `diagnosis_name`  VARCHAR(150) NOT NULL,
    `description`     TEXT         NULL,
    `diagnosed_date`  DATE         NOT NULL DEFAULT (CURRENT_DATE),
    `severity`        VARCHAR(20)  NULL,
    `notes`           VARCHAR(255) NULL,
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT `fk_diagnoses_visit` FOREIGN KEY (`visit_id`) REFERENCES `visits` (`visit_id`) ON DELETE CASCADE,
    INDEX `idx_diagnoses_visit_id` (`visit_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 10. prescriptions
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `prescriptions`;
CREATE TABLE `prescriptions` (
    `prescription_id`    INT AUTO_INCREMENT PRIMARY KEY,
    `visit_id`           INT NOT NULL,
    `doctor_id`          INT NOT NULL,
    `patient_id`         INT NOT NULL,
    `prescription_date`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `instructions`       TEXT     NULL,
    `created_at`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT `fk_prescriptions_visit`   FOREIGN KEY (`visit_id`)   REFERENCES `visits`   (`visit_id`)   ON DELETE CASCADE,
    CONSTRAINT `fk_prescriptions_doctor`  FOREIGN KEY (`doctor_id`)  REFERENCES `doctors`  (`doctor_id`)  ON DELETE RESTRICT,
    CONSTRAINT `fk_prescriptions_patient` FOREIGN KEY (`patient_id`) REFERENCES `patients` (`patient_id`) ON DELETE CASCADE,
    INDEX `idx_prescriptions_visit_id` (`visit_id`),
    INDEX `idx_prescriptions_patient_id` (`patient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 11. prescription_items
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `prescription_items`;
CREATE TABLE `prescription_items` (
    `item_id`          INT AUTO_INCREMENT PRIMARY KEY,
    `prescription_id`  INT NOT NULL,
    `medicine_name`    VARCHAR(150) NOT NULL,
    `dosage`           VARCHAR(50)  NULL,   -- e.g. 500 mg
    `frequency`        VARCHAR(50)  NULL,   -- e.g. 2 times/day
    `duration`         VARCHAR(50)  NULL,   -- e.g. 5 days
    `route`            VARCHAR(30)  NULL,   -- e.g. Oral
    `instructions`     VARCHAR(255) NULL,
    CONSTRAINT `fk_items_prescription` FOREIGN KEY (`prescription_id`) REFERENCES `prescriptions` (`prescription_id`) ON DELETE CASCADE,
    INDEX `idx_items_prescription_id` (`prescription_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 12. medical_tests
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `medical_tests`;
CREATE TABLE `medical_tests` (
    `test_id`       INT AUTO_INCREMENT PRIMARY KEY,
    `visit_id`      INT NOT NULL,
    `patient_id`    INT NOT NULL,
    `doctor_id`     INT NOT NULL,
    `test_name`     VARCHAR(150) NOT NULL,
    `test_type`     VARCHAR(50)  NULL,          -- BLOOD / X-RAY / MRI / CT / URINE ...
    `test_date`     DATE         NOT NULL DEFAULT (CURRENT_DATE),
    `result`        TEXT         NULL,
    `normal_range`  VARCHAR(100) NULL,
    `remarks`       VARCHAR(255) NULL,
    `status`        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT `fk_tests_visit`   FOREIGN KEY (`visit_id`)   REFERENCES `visits`   (`visit_id`)   ON DELETE CASCADE,
    CONSTRAINT `fk_tests_patient` FOREIGN KEY (`patient_id`) REFERENCES `patients` (`patient_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_tests_doctor`  FOREIGN KEY (`doctor_id`)  REFERENCES `doctors`  (`doctor_id`)  ON DELETE RESTRICT,
    CONSTRAINT `chk_tests_status` CHECK (`status` IN ('PENDING','COMPLETED')),
    INDEX `idx_tests_visit_id` (`visit_id`),
    INDEX `idx_tests_patient_id` (`patient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 13. test_reports
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `test_reports`;
CREATE TABLE `test_reports` (
    `report_id`     INT AUTO_INCREMENT PRIMARY KEY,
    `test_id`       INT NOT NULL,
    `file_name`     VARCHAR(255) NOT NULL,
    `file_path`     VARCHAR(500) NOT NULL,
    `file_type`     VARCHAR(10)  NULL,
    `file_size`     INT          NULL,
    `uploaded_by`   INT          NULL,
    `uploaded_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `description`   VARCHAR(255) NULL,
    CONSTRAINT `fk_reports_test`  FOREIGN KEY (`test_id`)     REFERENCES `medical_tests` (`test_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_reports_user`  FOREIGN KEY (`uploaded_by`) REFERENCES `users` (`user_id`) ON DELETE SET NULL,
    INDEX `idx_reports_test_id` (`test_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 14. audit_logs
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `audit_logs`;
CREATE TABLE `audit_logs` (
    `log_id`       INT AUTO_INCREMENT PRIMARY KEY,
    `user_id`      INT NULL,
    `action`       VARCHAR(30)  NULL,   -- CREATE / UPDATE / DELETE / LOGIN / LOGIN_FAILED / LOGOUT / APPROVE / REJECT / DOWNLOAD
    `table_name`   VARCHAR(50)  NULL,
    `record_id`    VARCHAR(50)  NULL,
    `description`  VARCHAR(255) NULL,
    `ip_address`   VARCHAR(45)  NULL,
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT `fk_audit_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE SET NULL,
    INDEX `idx_audit_user_id` (`user_id`),
    INDEX `idx_audit_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 15. refresh_tokens (supports POST /auth/refresh and server-side logout)
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `refresh_tokens`;
CREATE TABLE `refresh_tokens` (
    `refresh_token_id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`           INT NOT NULL,
    `token`             VARCHAR(255) NOT NULL,
    `expiry_date`       DATETIME(6)  NOT NULL,
    `created_at`        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT `uq_refresh_tokens_token` UNIQUE (`token`),
    CONSTRAINT `fk_refresh_tokens_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
    INDEX `idx_refresh_tokens_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------
-- Seed roles (required before creating any user)
-- ---------------------------------------------------------------------
INSERT INTO `roles` (`role_name`, `description`) VALUES
    ('ADMIN',  'Full system access — manages users, approvals, audit logs'),
    ('DOCTOR', 'Clinical workflow — visits, diagnoses, prescriptions, tests'),
    ('NURSE',  'Supports clinical workflow and patient record-keeping');

-- =====================================================================
-- V4 additions (email verification, reset tokens, assignments, appointments, vitals)
-- =====================================================================
-- 1. Email verification flag. Accounts that existed before this migration
--    (e.g. the seeded demo accounts) are treated as already verified.
ALTER TABLE `users` ADD COLUMN `email_verified` BIT(1) NOT NULL DEFAULT b'0';
UPDATE `users` SET `email_verified` = b'1';

-- 2. One-time tokens (SHA-256 hash stored, never the raw token)
CREATE TABLE `email_tokens` (
    `token_id`    BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`     INT NOT NULL,
    `token_hash`  VARCHAR(64) NOT NULL,
    `token_type`  VARCHAR(20) NOT NULL,
    `expiry_date` DATETIME(6) NOT NULL,
    `used`        BIT(1) NOT NULL DEFAULT b'0',
    `created_at`  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT `uq_email_tokens_hash` UNIQUE (`token_hash`),
    CONSTRAINT `fk_email_tokens_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
    CONSTRAINT `chk_email_tokens_type` CHECK (`token_type` IN ('EMAIL_VERIFY','PASSWORD_RESET')),
    INDEX `idx_email_tokens_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Patient assignment (a doctor and/or a nurse are responsible for a patient)
ALTER TABLE `patients`
    ADD COLUMN `assigned_doctor_id` INT NULL,
    ADD COLUMN `assigned_nurse_id`  INT NULL,
    ADD CONSTRAINT `fk_patients_assigned_doctor` FOREIGN KEY (`assigned_doctor_id`) REFERENCES `doctors` (`doctor_id`) ON DELETE SET NULL,
    ADD CONSTRAINT `fk_patients_assigned_nurse`  FOREIGN KEY (`assigned_nurse_id`)  REFERENCES `nurses` (`nurse_id`)  ON DELETE SET NULL,
    ADD INDEX `idx_patients_assigned_doctor` (`assigned_doctor_id`),
    ADD INDEX `idx_patients_assigned_nurse` (`assigned_nurse_id`);

-- 4. Appointments
CREATE TABLE `appointments` (
    `appointment_id` INT AUTO_INCREMENT PRIMARY KEY,
    `patient_id`     INT NOT NULL,
    `doctor_id`      INT NOT NULL,
    `scheduled_at`   DATETIME NOT NULL,
    `reason`         VARCHAR(255) NULL,
    `notes`          VARCHAR(500) NULL,
    `status`         VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT `fk_appointments_patient` FOREIGN KEY (`patient_id`) REFERENCES `patients` (`patient_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_appointments_doctor`  FOREIGN KEY (`doctor_id`)  REFERENCES `doctors` (`doctor_id`)  ON DELETE RESTRICT,
    CONSTRAINT `chk_appointments_status` CHECK (`status` IN ('SCHEDULED','COMPLETED','CANCELLED')),
    INDEX `idx_appointments_patient` (`patient_id`),
    INDEX `idx_appointments_doctor_time` (`doctor_id`, `scheduled_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. Vitals
CREATE TABLE `vitals` (
    `vital_id`         INT AUTO_INCREMENT PRIMARY KEY,
    `patient_id`       INT NOT NULL,
    `recorded_by`      INT NULL,
    `temperature_c`    DECIMAL(4,1) NULL,
    `pulse_bpm`        INT NULL,
    `systolic_bp`      INT NULL,
    `diastolic_bp`     INT NULL,
    `respiratory_rate` INT NULL,
    `spo2`             INT NULL,
    `weight_kg`        DECIMAL(5,1) NULL,
    `notes`            VARCHAR(500) NULL,
    `recorded_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT `fk_vitals_patient` FOREIGN KEY (`patient_id`) REFERENCES `patients` (`patient_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_vitals_user`    FOREIGN KEY (`recorded_by`) REFERENCES `users` (`user_id`) ON DELETE SET NULL,
    INDEX `idx_vitals_patient_time` (`patient_id`, `recorded_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
