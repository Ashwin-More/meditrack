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
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/** Issues and consumes one-time tokens. Raw tokens only ever exist in the email; the DB stores a SHA-256 hash. */
@Service
@RequiredArgsConstructor
public class EmailTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailTokenRepository repository;

    @Value("${app.tokens.email-verify-hours:24}")
    private long verifyHours;

    @Value("${app.tokens.password-reset-minutes:30}")
    private long resetMinutes;

    /** Creates a new token (invalidating any earlier one of the same type) and returns the RAW value to email. */
    @Transactional
    public String issue(User user, TokenType type) {
        repository.deleteAllByUserIdAndType(user.getUserId(), type);

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        LocalDateTime expiry = type == TokenType.EMAIL_VERIFY
                ? LocalDateTime.now().plusHours(verifyHours)
                : LocalDateTime.now().plusMinutes(resetMinutes);

        repository.save(EmailToken.builder()
                .user(user)
                .tokenHash(sha256(raw))
                .tokenType(type)
                .expiryDate(expiry)
                .build());
        return raw;
    }

    /** Validates the raw token, marks it used, and returns its owner. */
    @Transactional
    public User consume(String rawToken, TokenType type) {
        EmailToken token = repository.findByTokenHashAndTokenType(sha256(rawToken == null ? "" : rawToken.trim()), type)
                .orElseThrow(() -> new BadRequestException("This link is invalid. Please request a new one"));
        if (token.isUsed() || token.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("This link has expired or was already used. Please request a new one");
        }
        token.setUsed(true);
        repository.save(token);
        return token.getUser();
    }

    /** Nightly housekeeping: drop tokens that are expired or already used. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeOldTokens() {
        repository.deleteExpiredOrUsed(LocalDateTime.now());
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
