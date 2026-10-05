package com.meditrack.service;

import com.meditrack.entity.EmailToken;
import com.meditrack.entity.User;
import com.meditrack.enums.TokenType;
import com.meditrack.exception.BadRequestException;
import com.meditrack.repository.EmailTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailTokenServiceTest {

    @Mock private EmailTokenRepository repository;

    private EmailTokenService service;
    private final User user = User.builder().userId(7).build();

    @BeforeEach
    void setUp() {
        service = new EmailTokenService(repository);
        ReflectionTestUtils.setField(service, "verifyMinutes", 10L);
        ReflectionTestUtils.setField(service, "resetMinutes", 30L);
        ReflectionTestUtils.setField(service, "loginOtpMinutes", 5L);
        ReflectionTestUtils.setField(service, "maxAttempts", 5);
        ReflectionTestUtils.setField(service, "resendCooldownSeconds", 30L);
        ReflectionTestUtils.setField(service, "hashKey", "unit-test-secret-key-0123456789abcdef");
    }

    private EmailToken tokenFor(String code, TokenType type, LocalDateTime expiry) {
        return EmailToken.builder().user(user).tokenType(type)
                .tokenHash(service.hash(user, type, code)).expiryDate(expiry).build();
    }

    private void latestIs(EmailToken token, TokenType type) {
        when(repository.findFirstByUser_UserIdAndTokenTypeOrderByTokenIdDesc(7, type)).thenReturn(Optional.ofNullable(token));
    }

    private String rejected(String code, TokenType type) {
        return assertThrows(BadRequestException.class, () -> service.consume(user, code, type)).getMessage();
    }

    @Test
    void issue_storesOnlyAKeyedHash_deletesOlderCode_andReturnsA6DigitCode() {
        String raw = service.issue(user, TokenType.EMAIL_VERIFY);

        assertTrue(Pattern.matches("\\d{6}", raw), "OTP should be exactly 6 digits: " + raw);
        ArgumentCaptor<EmailToken> saved = ArgumentCaptor.forClass(EmailToken.class);
        verify(repository).deleteAllByUserIdAndType(7, TokenType.EMAIL_VERIFY); // previous OTP invalidated
        verify(repository).save(saved.capture());
        assertNotEquals(raw, saved.getValue().getTokenHash());
        assertEquals(service.hash(user, TokenType.EMAIL_VERIFY, raw), saved.getValue().getTokenHash());
        assertTrue(saved.getValue().getExpiryDate().isAfter(LocalDateTime.now().plusMinutes(9)));
        assertTrue(saved.getValue().getExpiryDate().isBefore(LocalDateTime.now().plusMinutes(11)));
    }

    @Test
    void hash_isBoundToUserAndPurpose() {
        User other = User.builder().userId(8).build();
        assertNotEquals(service.hash(user, TokenType.EMAIL_VERIFY, "123456"), service.hash(other, TokenType.EMAIL_VERIFY, "123456"));
        assertNotEquals(service.hash(user, TokenType.EMAIL_VERIFY, "123456"), service.hash(user, TokenType.PASSWORD_RESET, "123456"));
    }

    @Test
    void consume_validOtp_marksUsed() {
        EmailToken token = tokenFor("123456", TokenType.PASSWORD_RESET, LocalDateTime.now().plusMinutes(5));
        latestIs(token, TokenType.PASSWORD_RESET);

        assertDoesNotThrow(() -> service.consume(user, "123456", TokenType.PASSWORD_RESET));
        assertTrue(token.isUsed());
    }

    @Test
    void consume_wrongOtp_isInvalid_andCountsAnAttempt() {
        EmailToken token = tokenFor("123456", TokenType.EMAIL_VERIFY, LocalDateTime.now().plusMinutes(5));
        latestIs(token, TokenType.EMAIL_VERIFY);

        assertEquals(EmailTokenService.MSG_INVALID, rejected("000000", TokenType.EMAIL_VERIFY));
        assertEquals(1, token.getAttempts());
        assertFalse(token.isUsed());
    }

    @Test
    void consume_fifthWrongGuess_locksTheCode_evenForTheCorrectOtp() {
        EmailToken token = tokenFor("123456", TokenType.EMAIL_VERIFY, LocalDateTime.now().plusMinutes(5));
        token.setAttempts(4);
        latestIs(token, TokenType.EMAIL_VERIFY);

        assertEquals(EmailTokenService.MSG_TOO_MANY, rejected("000000", TokenType.EMAIL_VERIFY));
        assertEquals(EmailTokenService.MSG_TOO_MANY, rejected("123456", TokenType.EMAIL_VERIFY)); // right code, still locked
        assertFalse(token.isUsed());
    }

    @Test
    void consume_expiredOtp_saysExpired() {
        latestIs(tokenFor("123456", TokenType.EMAIL_VERIFY, LocalDateTime.now().minusMinutes(1)), TokenType.EMAIL_VERIFY);
        assertEquals(EmailTokenService.MSG_EXPIRED, rejected("123456", TokenType.EMAIL_VERIFY));
    }

    @Test
    void consume_alreadyUsedOtp_saysUsed_andCannotBeReplayed() {
        EmailToken token = tokenFor("123456", TokenType.EMAIL_VERIFY, LocalDateTime.now().plusMinutes(5));
        token.setUsed(true);
        latestIs(token, TokenType.EMAIL_VERIFY);
        assertEquals(EmailTokenService.MSG_USED, rejected("123456", TokenType.EMAIL_VERIFY));
    }

    @Test
    void consume_oldOtpAfterANewOneWasIssued_isInvalid() {
        // Only the newest code exists (issue() deletes the rest); the previous code no longer matches it.
        latestIs(tokenFor("654321", TokenType.EMAIL_VERIFY, LocalDateTime.now().plusMinutes(5)), TokenType.EMAIL_VERIFY);
        assertEquals(EmailTokenService.MSG_INVALID, rejected("123456", TokenType.EMAIL_VERIFY));
    }

    @Test
    void consume_noCodeIssued_emptyOrMalformed_areInvalid() {
        latestIs(null, TokenType.EMAIL_VERIFY);
        assertEquals(EmailTokenService.MSG_INVALID, rejected("123456", TokenType.EMAIL_VERIFY));
        assertEquals(EmailTokenService.MSG_INVALID, rejected("", TokenType.EMAIL_VERIFY));
        assertEquals(EmailTokenService.MSG_INVALID, rejected(null, TokenType.EMAIL_VERIFY));
        assertEquals(EmailTokenService.MSG_INVALID, rejected("12ab56", TokenType.EMAIL_VERIFY));
        assertEquals(EmailTokenService.MSG_INVALID, rejected("1234567", TokenType.EMAIL_VERIFY));
    }

    @Test
    void consume_codeIssuedForAnotherPurpose_isNotAccepted() {
        latestIs(null, TokenType.PASSWORD_RESET); // a reset code was never issued, only a verify code
        assertEquals(EmailTokenService.MSG_INVALID, rejected("123456", TokenType.PASSWORD_RESET));
    }

    @Test
    void resendCooldown_blocksASecondCodeWithinThirtySeconds() {
        EmailToken fresh = tokenFor("123456", TokenType.EMAIL_VERIFY, LocalDateTime.now().plusMinutes(10));
        fresh.setCreatedAt(LocalDateTime.now().minusSeconds(5));
        latestIs(fresh, TokenType.EMAIL_VERIFY);
        assertTrue(service.isInResendCooldown(user, TokenType.EMAIL_VERIFY));

        fresh.setCreatedAt(LocalDateTime.now().minusSeconds(45));
        assertFalse(service.isInResendCooldown(user, TokenType.EMAIL_VERIFY));
    }
}
