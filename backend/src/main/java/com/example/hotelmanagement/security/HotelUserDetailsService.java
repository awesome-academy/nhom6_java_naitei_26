package com.example.hotelmanagement.security;

import com.example.hotelmanagement.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HotelUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(username)
            .map(UserPrincipal::from)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    @Transactional(readOnly = true)
    public UserPrincipal loadUserByPublicId(String publicId) {
        return userRepository.findByPublicIdAndDeletedAtIsNull(publicId)
            .map(UserPrincipal::from)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
