package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = {
        "userRoles",
        "userRoles.role",
        "userRoles.role.rolePermissions",
        "userRoles.role.rolePermissions.permission"
    })
    Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    @EntityGraph(attributePaths = {
        "userRoles",
        "userRoles.role",
        "userRoles.role.rolePermissions",
        "userRoles.role.rolePermissions.permission"
    })
    Optional<User> findByPublicIdAndDeletedAtIsNull(String publicId);

    @EntityGraph(attributePaths = {
        "userRoles",
        "userRoles.role"
    })
    List<User> findAllByDeletedAtIsNullOrderByCreatedAtDesc();

    Optional<User> findByPhoneAndDeletedAtIsNull(String phone);

    boolean existsByEmailIgnoreCaseAndDeletedAtIsNull(String email);
}
