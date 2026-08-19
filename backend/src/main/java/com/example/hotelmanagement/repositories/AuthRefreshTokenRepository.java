package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.AuthRefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AuthRefreshTokenRepository extends JpaRepository<AuthRefreshToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select token
        from AuthRefreshToken token
        join fetch token.user
        where token.jwtId = :jwtId
        """)
    Optional<AuthRefreshToken> findByJwtIdForUpdate(@Param("jwtId") String jwtId);
}
