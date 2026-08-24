package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.RoomType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomTypeRepository extends JpaRepository<RoomType, Long> {

    Optional<RoomType> findByIdAndDeletedAtIsNull(Long id);

    @EntityGraph(attributePaths = {"beds", "amenities", "cancellationPolicy", "cancellationPolicy.rules"})
    List<RoomType> findAllByDeletedAtIsNullOrderBySortOrderAscNameAsc();

    @EntityGraph(attributePaths = {"beds", "amenities", "cancellationPolicy", "cancellationPolicy.rules"})
    Optional<RoomType> findByCodeIgnoreCaseAndDeletedAtIsNull(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT roomType
            FROM RoomType roomType
            WHERE UPPER(roomType.code) = UPPER(:code)
              AND roomType.deletedAt IS NULL
            """)
    Optional<RoomType> findForUpdateByCode(@Param("code") String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    boolean existsByCancellationPolicy_CodeIgnoreCaseAndDeletedAtIsNullAndIsActiveTrue(String code);

    long countByDeletedAtIsNullAndIsActiveTrue();

    long countByIsActiveFalse();
}
