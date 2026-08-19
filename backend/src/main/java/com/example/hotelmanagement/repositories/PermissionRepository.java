package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    List<Permission> findAllByOrderByResourceAscActionAscCodeAsc();

    List<Permission> findAllByCodeIn(Collection<String> codes);

    Optional<Permission> findByCode(String code);
}
