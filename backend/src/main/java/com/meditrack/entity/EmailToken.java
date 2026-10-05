package com.meditrack.entity;

import com.meditrack.enums.TokenType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** One-time token for email verification / password reset. Only the SHA-256 hash is stored. */
@Entity
@Table(name = "email_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id")
    private Long tokenId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Not globally unique — short numeric OTP codes can collide across different users; scope lookups to (user, type). */
    @Column(name = "token_hash", length = 64, nullable = false)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", length = 20, nullable = false)
    private TokenType tokenType;

    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;

    @Column(name = "used", nullable = false)
    @Builder.Default
    private boolean used = false;

    /** Wrong guesses made against this code; the code is locked once the configured maximum is reached. */
    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
