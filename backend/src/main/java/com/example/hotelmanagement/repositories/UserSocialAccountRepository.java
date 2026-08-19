package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.UserSocialAccount;
import com.example.hotelmanagement.entity.enums.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserSocialAccountRepository extends JpaRepository<UserSocialAccount, Long> {

    Optional<UserSocialAccount> findByProviderAndProviderUserId(
        OAuthProvider provider,
        String providerUserId
    );
}
