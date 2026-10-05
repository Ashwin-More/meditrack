package com.meditrack.service;

import com.meditrack.entity.EmailToken;
import com.meditrack.entity.User;
import com.meditrack.enums.TokenType;
import com.meditrack.exception.BadRequestException;
import com.meditrack.repository.EmailTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Issues and consumes one-time OTP codes (6 digits).
 *
 * - Generated with {@link SecureRandom}.
 * - Never stored in plaintext: the DB keeps an HMAC-SHA256 of (userId, purpose, code) keyed with the
 *   server secret, so a leaked database cannot be reversed by trying all 1,000,000 codes.
 * - Only the newest code per (user, purpose) exists: issuing a new one deletes the previous one.
 * - Single use, time limited, and locked after {@code app.otp.max-attempts} wrong guesses.
 */
@Service
@RequiredArgsConstructor
public class EmailTokenService {

    public static final String MSG_INVALID = "Invalid OTP. Please try again.";
    public static final String MSG_EXPIRED = "OTP has expired. Please request a new OTP.";
    public static final String MSG_USED = "This OTP has already been used. Please request a new OTP.";
    public static final String MSG_TOO_MANY = "Too many verification attempts. Please request a new OTP.";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int OTP_DIGITS = 6;
    private static final int OTP_BOUND = 1_000_000; // 10^OTP_DIGITS

    private final EmailTokenRepository repository;

    @Value("${app.tokens.email-verify-minutes:10}")
    private long verifyMinutes;

    @Value("${app.tokens.password-reset-minutes:30}")
    private long resetMinutes;

    @Value("${app.tokens.login-otp-minutes:5}")
    private long loginOtpMinutes;

    @Value("${app.otp.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.otp.resend-cooldown-seconds:30}")
    private long resendCooldownSeconds;

    /** Same secret the JWT signer uses; only used as the HMAC key for stored OTP hashes. */
    @Value("${app.jwt.secret:change-this-super-secret-key-meditrack-32chars-min}")
    private String hashKey;

    /** Creates a new OTP (invalidating any earlier one of the same type for this user) and returns the RAW code to send. */
    @Transactional
    public String issue(User user, TokenType type) {
        repository.deleteAllByUserIdAndType(user.getUserId(), type);

        String raw = String.format("%0" + OTP_DIGITS + "d", RANDOM.nextInt(OTP_BOUND));

        LocalDateTime expiry = switch (type) {
            case EMAIL_VERIFY -> LocalDateTime.now().plusMinutes(verifyMinutes);
            case PASSWORD_RESET -> LocalDateTime.now().plusMinutes(resetMinutes);
            case LOGIN_OTP -> LocalDateTime.now().plusMinutes(loginOtpMinutes);
        };

        repository.save(EmailToken.builder()
                .user(user)
                .tokenHash(hash(user, type, raw))
                .tokenType(type)
                .expiryDate(expiry)
                .build());
        return raw;
    }

    /** True if a code for this user + purpose was issued less than the resend cooldown ago. */
    @Transactional(readOnly = true)
    public boolean isInResendCooldown(User user, TokenType type) {
        return repository.findFirstByUser_UserIdAndTokenTypeOrderByTokenIdDesc(user.getUserId(), type)
                .map(t -> t.getCreatedAt() != null
                        && t.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(resendCooldownSeconds)))
                .orElse(false);
    }

    /**
     * Validates the raw OTP for this specific user and purpose and marks it used.
     * Runs in its own transaction that is NOT rolled back on a rejected code, so the wrong-attempt
     * counter and the used flag are saved even though the caller's request ends in an error.
     * Throws {@link BadRequestException} with a specific message (invalid / expired / used / too many attempts).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = BadRequestException.class)
    public void consume(User user, String rawOtp, TokenType type) {
        String normalized = rawOtp == null ? "" : rawOtp.trim();
        if (!normalized.matches("\\d{" + OTP_DIGITS + "}")) {
            throw new BadRequestException(MSG_INVALID);
        }
        Optional<EmailToken> latest = repository.findFirstByUser_UserIdAndTokenTypeOrderByTokenIdDesc(user.getUserId(), type);
        if (latest.isEmpty()) {
            throw new BadRequestException(MSG_INVALID);
        }
        EmailToken token = latest.get();

        if (token.getAttempts() >= maxAttempts) {
            throw new BadRequestException(MSG_TOO_MANY);
        }

        boolean matches = MessageDigest.isEqual(
                hash(user, type, normalized).getBytes(StandardCharsets.UTF_8),
                token.getTokenHash().getBytes(StandardCharsets.UTF_8));

        if (!matches) {
            // Only live codes accumulate strikes; a used/expired code can't be guessed into validity anyway.
            if (!token.isUsed() && !token.getExpiryDate().isBefore(LocalDateTime.now())) {
                token.setAttempts(token.getAttempts() + 1);
                repository.save(token);
                if (token.getAttempts() >= maxAttempts) {
                    throw new BadRequestException(MSG_TOO_MANY);
                }
            }
            throw new BadRequestException(MSG_INVALID);
        }
        if (token.isUsed()) {
            throw new BadRequestException(MSG_USED);
        }
        if (token.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new BadRequestException(MSG_EXPIRED);
        }
        token.setUsed(true);
        repository.save(token);
    }

    /** Nightly housekeeping: drop tokens that are expired or already used. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeOldTokens() {
        repository.deleteExpiredOrUsed(LocalDateTime.now());
    }

    String hash(User user, TokenType type, String rawOtp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((user.getUserId() + ":" + type.name() + ":" + rawOtp).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
