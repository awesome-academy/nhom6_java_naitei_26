package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.BookingSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookingSourceRepository extends JpaRepository<BookingSource, Long> {

    Optional<BookingSource> findByCodeIgnoreCaseAndIsActiveTrue(String code);
}
