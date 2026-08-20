package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.CustomerProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<CustomerProfile> findByUser_Id(Long userId);

    boolean existsByUser_Id(Long userId);
}
