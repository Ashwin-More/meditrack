-- =====================================================================
-- MediTrack — V4
--   * users.email_verified          (email verification for all roles)
--   * email_tokens                  (email-verification + password-reset tokens, stored hashed)
--   * patients.assigned_doctor_id / assigned_nurse_id  (assigned-patient access model)
--   * appointments                  (doctor schedule; nurses get read-only access)
--   * vitals                        (nursing/care observations)
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
