-- OTP brute-force protection: count wrong guesses per issued code.
ALTER TABLE `email_tokens` ADD COLUMN `attempts` INT NOT NULL DEFAULT 0 AFTER `used`;
