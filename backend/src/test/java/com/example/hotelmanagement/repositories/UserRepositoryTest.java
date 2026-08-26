package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.Role;
import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.UserRole;
import com.example.hotelmanagement.entity.enums.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void customerListExcludesUsersWithStaffRole() {
        Role customerRole = roleRepository.save(role("CUSTOMER"));
        Role staffRole = roleRepository.save(role("STAFF"));

        userRepository.saveAndFlush(userWithRoles(
                "customer-only@example.com", customerRole
        ));
        userRepository.saveAndFlush(userWithRoles("staff@example.com", staffRole));

        Page<User> result = userRepository.findCustomerUsers(
                UserStatus.ACTIVE,
                "",
                PageRequest.of(0, 20)
        );

        assertThat(result.getContent())
                .extracting(User::getEmail)
                .containsExactly("customer-only@example.com");
    }

    private Role role(String code) {
        return Role.builder()
                .code(code)
                .name(code)
                .isSystem(true)
                .build();
    }

    private User userWithRoles(String email, Role... roles) {
        User user = User.builder()
                .publicId(email)
                .email(email)
                .fullName(email)
                .status(UserStatus.ACTIVE)
                .failedLoginCount(0)
                .build();
        for (Role role : roles) {
            user.getUserRoles().add(UserRole.builder()
                    .user(user)
                    .role(role)
                    .assignedAt(OffsetDateTime.of(2026, 8, 26, 0, 0, 0, 0, ZoneOffset.UTC))
                    .build());
        }
        return user;
    }
}
