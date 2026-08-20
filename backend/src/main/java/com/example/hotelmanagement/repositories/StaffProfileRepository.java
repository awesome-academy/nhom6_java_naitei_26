package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.StaffProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StaffProfileRepository extends JpaRepository<StaffProfile, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<StaffProfile> findByEmployeeCodeIgnoreCase(String employeeCode);

    @EntityGraph(attributePaths = "user")
    Optional<StaffProfile> findByUser_Id(Long userId);

    boolean existsByUser_Id(Long userId);
}
