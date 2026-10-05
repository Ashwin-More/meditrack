package com.meditrack.repository;

import com.meditrack.entity.EmailToken;
import com.meditrack.enums.TokenType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {

    /**
     * OTP codes are short (6 digits), so unlike the old random link tokens the
     * hash is NOT globally unique — two different users could legitimately draw
     * the same code. Lookups are therefore always scoped to one user + one type.
     */
    Optional<EmailToken> findByUser_UserIdAndTokenHashAndTokenType(Integer userId, String tokenHash, TokenType tokenType);

    /** The most recently issued code for this user + purpose (older ones are deleted on re-issue). */
    Optional<EmailToken> findFirstByUser_UserIdAndTokenTypeOrderByTokenIdDesc(Integer userId, TokenType tokenType);

    @Modifying
    @Query("DELETE FROM EmailToken t WHERE t.user.userId = :userId AND t.tokenType = :type")
    void deleteAllByUserIdAndType(@Param("userId") Integer userId, @Param("type") TokenType type);

    @Modifying
    @Query("DELETE FROM EmailToken t WHERE t.expiryDate < :cutoff OR t.used = true")
    int deleteExpiredOrUsed(@Param("cutoff") LocalDateTime cutoff);
}
