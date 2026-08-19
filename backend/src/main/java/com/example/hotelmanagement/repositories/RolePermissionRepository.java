package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermission.RolePermissionId> {
}
