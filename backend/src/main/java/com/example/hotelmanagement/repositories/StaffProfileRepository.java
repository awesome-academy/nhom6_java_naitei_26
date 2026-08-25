package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.StaffProfile;
import com.example.hotelmanagement.entity.enums.EmploymentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface StaffProfileRepository extends JpaRepository<StaffProfile, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<StaffProfile> findByEmployeeCodeIgnoreCase(String employeeCode);

    @EntityGraph(attributePaths = "user")
    Optional<StaffProfile> findByUser_Id(Long userId);

    @EntityGraph(attributePaths = "user")
    List<StaffProfile> findByEmploymentStatusAndUser_DeletedAtIsNullOrderByEmployeeCodeAsc(
            EmploymentStatus employmentStatus
    );

    @EntityGraph(attributePaths = "user")
    List<StaffProfile> findByUser_DeletedAtIsNullOrderByEmployeeCodeAsc();

    boolean existsByUser_Id(Long userId);
}
