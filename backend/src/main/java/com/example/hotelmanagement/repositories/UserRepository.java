package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.example.hotelmanagement.entity.enums.UserStatus;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

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

    @EntityGraph(attributePaths = {
        "userRoles",
        "userRoles.role"
    })
    @Query("""
        SELECT DISTINCT user FROM User user
        WHERE user.deletedAt IS NULL
          AND (:status IS NULL OR user.status = :status)
          AND EXISTS (
              SELECT userRole FROM UserRole userRole
              WHERE userRole.user = user
                AND userRole.role.code = 'CUSTOMER'
          )
          AND NOT EXISTS (
              SELECT userRole FROM UserRole userRole
              WHERE userRole.user = user
                AND userRole.role.code = 'STAFF'
          )
          AND (
              :search = ''
              OR LOWER(user.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
              OR LOWER(user.email) LIKE LOWER(CONCAT('%', :search, '%'))
              OR LOWER(COALESCE(user.phone, '')) LIKE LOWER(CONCAT('%', :search, '%'))
          )
        ORDER BY user.createdAt DESC
        """)
    Page<User> findCustomerUsers(
        @Param("status") UserStatus status,
        @Param("search") String search,
        Pageable pageable
    );

    Optional<User> findByPhoneAndDeletedAtIsNull(String phone);

    boolean existsByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    boolean existsByEmailIgnoreCase(String email);
}
