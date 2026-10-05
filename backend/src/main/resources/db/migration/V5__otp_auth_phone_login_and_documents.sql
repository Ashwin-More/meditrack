-- =====================================================================
-- MediTrack — V5
--   * Replaces link-based email tokens with short numeric OTP codes:
--     token_hash is no longer globally unique (two users could draw the
--     same 6-digit code), so lookups are now scoped by (user_id, type).
--   * users.phone becomes a second login identifier alongside
--     username/email, so it must be unique when present.
--   * patient_documents — the "Medical Documents / Attachments" feature
--     (previous hospital records, discharge summaries, insurance docs,
--     etc.), separate from medical_tests/test_reports.
-- =====================================================================

-- 1. OTP tokens: drop the global uniqueness on the hash (collisions are
--    now possible across different users) and look up per-user instead.
ALTER TABLE `email_tokens` DROP INDEX `uq_email_tokens_hash`;
ALTER TABLE `email_tokens` ADD INDEX `idx_email_tokens_user_type_hash` (`user_id`, `token_type`, `token_hash`);

-- Widen the allowed token types to include passwordless-login OTPs.
ALTER TABLE `email_tokens` DROP CHECK `chk_email_tokens_type`;
ALTER TABLE `email_tokens` ADD CONSTRAINT `chk_email_tokens_type`
    CHECK (`token_type` IN ('EMAIL_VERIFY','PASSWORD_RESET','LOGIN_OTP'));

-- 2. Phone becomes a login identifier: must be unique when set.
--    (existing seeded rows have no phone yet, so this is safe to add now.)
ALTER TABLE `users` ADD UNIQUE INDEX `uq_users_phone` (`phone`);

-- 3. Medical Documents / Attachments
CREATE TABLE `patient_documents` (
    `document_id`   INT AUTO_INCREMENT PRIMARY KEY,
    `patient_id`    INT NOT NULL,
    `document_name` VARCHAR(150) NOT NULL,
    `document_type` VARCHAR(30)  NOT NULL DEFAULT 'OTHER',
    `file_name`     VARCHAR(255) NOT NULL,
    `file_path`     VARCHAR(500) NOT NULL,
    `file_type`     VARCHAR(10)  NULL,
    `file_size`     INT          NULL,
    `description`   VARCHAR(255) NULL,
    `uploaded_by`   INT          NULL,
    `uploaded_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT `fk_documents_patient` FOREIGN KEY (`patient_id`) REFERENCES `patients` (`patient_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_documents_user`    FOREIGN KEY (`uploaded_by`) REFERENCES `users` (`user_id`) ON DELETE SET NULL,
    CONSTRAINT `chk_documents_type` CHECK (`document_type` IN (
        'PREVIOUS_RECORD','LAB_REPORT','XRAY_SCAN','PRESCRIPTION_DOC',
        'DISCHARGE_SUMMARY','INSURANCE','OTHER'
    )),
    INDEX `idx_documents_patient_id` (`patient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
