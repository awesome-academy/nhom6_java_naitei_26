package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRole.UserRoleId> {

    long deleteByUser_Id(Long userId);
}
