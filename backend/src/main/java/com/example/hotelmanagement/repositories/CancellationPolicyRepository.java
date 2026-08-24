package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.CancellationPolicy;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CancellationPolicyRepository extends JpaRepository<CancellationPolicy, Long> {

    @EntityGraph(attributePaths = "rules")
    List<CancellationPolicy> findAllByOrderByIsDefaultDescCodeAsc();

    @EntityGraph(attributePaths = "rules")
    List<CancellationPolicy> findAllByIsActiveTrueOrderByIsDefaultDescCodeAsc();

    @EntityGraph(attributePaths = "rules")
    Optional<CancellationPolicy> findByCodeIgnoreCase(String code);

    @EntityGraph(attributePaths = "rules")
    Optional<CancellationPolicy> findByCodeIgnoreCaseAndIsActiveTrue(String code);

    boolean existsByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT policy FROM CancellationPolicy policy WHERE policy.isDefault = true")
    List<CancellationPolicy> findDefaultPoliciesForUpdate();
}
