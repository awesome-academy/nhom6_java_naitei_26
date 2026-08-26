package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.AuthToken;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.enums.AuthTokenType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select token
        from AuthToken token
        join fetch token.user
        where token.tokenHash = :tokenHash
        """)
    Optional<AuthToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Query("""
        select token
        from AuthToken token
        join fetch token.user
        where token.tokenHash = :tokenHash
        """)
    Optional<AuthToken> findByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("""
        update AuthToken token
        set token.expiresAt = :expiredAt
        where token.user = :user
            and token.tokenType = :tokenType
            and token.usedAt is null
            and token.expiresAt > :expiredAt
        """)
    int expireActiveTokens(
        @Param("user") User user,
        @Param("tokenType") AuthTokenType tokenType,
        @Param("expiredAt") OffsetDateTime expiredAt
    );

    boolean existsByUserAndTokenTypeAndCreatedAtAfter(
        User user,
        AuthTokenType tokenType,
        OffsetDateTime createdAt
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        delete from AuthToken token
        where token.expiresAt < :cutoff
        """)
    int deleteExpiredBefore(@Param("cutoff") OffsetDateTime cutoff);
}
