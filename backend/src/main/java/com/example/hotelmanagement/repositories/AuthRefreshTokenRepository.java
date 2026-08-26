package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.AuthRefreshToken;
import com.example.hotelmanagement.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.OffsetDateTime;

public interface AuthRefreshTokenRepository extends JpaRepository<AuthRefreshToken, Long> {

    @Modifying
    @Query("update AuthRefreshToken token set token.revokedAt = :revokedAt "
            + "where token.user = :user and token.revokedAt is null")
    int revokeAllForUser(@Param("user") User user, @Param("revokedAt") OffsetDateTime revokedAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select token
        from AuthRefreshToken token
        join fetch token.user
        where token.jwtId = :jwtId
        """)
    Optional<AuthRefreshToken> findByJwtIdForUpdate(@Param("jwtId") String jwtId);
}
