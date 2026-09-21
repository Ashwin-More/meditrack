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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailTokenServiceTest {

    @Mock private EmailTokenRepository repository;

    private EmailTokenService service;

    @BeforeEach
    void setUp() {
        service = new EmailTokenService(repository);
        ReflectionTestUtils.setField(service, "verifyHours", 24L);
        ReflectionTestUtils.setField(service, "resetMinutes", 30L);
    }

    @Test
    void issue_storesOnlyTheHash_andReturnsTheRawToken() {
        User user = User.builder().userId(7).build();

        String raw = service.issue(user, TokenType.EMAIL_VERIFY);

        ArgumentCaptor<EmailToken> saved = ArgumentCaptor.forClass(EmailToken.class);
        verify(repository).deleteAllByUserIdAndType(7, TokenType.EMAIL_VERIFY);
        verify(repository).save(saved.capture());
        assertNotEquals(raw, saved.getValue().getTokenHash());
        assertEquals(EmailTokenService.sha256(raw), saved.getValue().getTokenHash());
        assertTrue(saved.getValue().getExpiryDate().isAfter(LocalDateTime.now().plusHours(23)));
    }

    @Test
    void consume_validToken_marksUsed() {
        User user = User.builder().userId(7).build();
        EmailToken token = EmailToken.builder().user(user).tokenType(TokenType.PASSWORD_RESET)
                .expiryDate(LocalDateTime.now().plusMinutes(5)).build();
        when(repository.findByTokenHashAndTokenType(EmailTokenService.sha256("abc"), TokenType.PASSWORD_RESET))
                .thenReturn(Optional.of(token));

        assertSame(user, service.consume("abc", TokenType.PASSWORD_RESET));
        assertTrue(token.isUsed());
    }

    @Test
    void consume_expiredToken_isRejected() {
        EmailToken token = EmailToken.builder().tokenType(TokenType.EMAIL_VERIFY)
                .expiryDate(LocalDateTime.now().minusMinutes(1)).build();
        when(repository.findByTokenHashAndTokenType(EmailTokenService.sha256("abc"), TokenType.EMAIL_VERIFY))
                .thenReturn(Optional.of(token));

        assertThrows(BadRequestException.class, () -> service.consume("abc", TokenType.EMAIL_VERIFY));
    }

    @Test
    void consume_alreadyUsedToken_isRejected() {
        EmailToken token = EmailToken.builder().tokenType(TokenType.EMAIL_VERIFY).used(true)
                .expiryDate(LocalDateTime.now().plusHours(1)).build();
        when(repository.findByTokenHashAndTokenType(EmailTokenService.sha256("abc"), TokenType.EMAIL_VERIFY))
                .thenReturn(Optional.of(token));

        assertThrows(BadRequestException.class, () -> service.consume("abc", TokenType.EMAIL_VERIFY));
    }

    @Test
    void consume_unknownToken_isRejected() {
        when(repository.findByTokenHashAndTokenType(anyString(), any())).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> service.consume("nope", TokenType.EMAIL_VERIFY));
    }
}
