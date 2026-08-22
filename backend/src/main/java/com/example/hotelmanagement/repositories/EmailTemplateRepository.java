package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.EmailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {

    Optional<EmailTemplate> findByCodeAndIsActiveTrue(String code);

    Optional<EmailTemplate> findByCode(String code);
}
