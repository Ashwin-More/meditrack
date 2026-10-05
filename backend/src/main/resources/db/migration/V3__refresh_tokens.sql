-- =====================================================================
-- MediTrack — V3: refresh_tokens table (supports POST /auth/refresh
-- and server-side logout / token revocation).
-- =====================================================================

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
