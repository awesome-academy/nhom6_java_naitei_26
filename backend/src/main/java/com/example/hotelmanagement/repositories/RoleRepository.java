package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    @EntityGraph(attributePaths = {
        "rolePermissions",
        "rolePermissions.permission"
    })
    List<Role> findAllByOrderByCodeAsc();

    @EntityGraph(attributePaths = {
        "rolePermissions",
        "rolePermissions.permission"
    })
    Optional<Role> findByCode(String code);

    boolean existsByCode(String code);
}
