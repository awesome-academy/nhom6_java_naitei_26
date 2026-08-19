package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.Shift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftRepository extends JpaRepository<Shift, Long> {

    List<Shift> findAllByOrderByCodeAsc();

    Optional<Shift> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
