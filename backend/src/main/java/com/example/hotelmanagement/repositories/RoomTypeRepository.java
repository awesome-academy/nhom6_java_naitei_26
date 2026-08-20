package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.RoomType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomTypeRepository extends JpaRepository<RoomType, Long> {

    Optional<RoomType> findByIdAndDeletedAtIsNull(Long id);

    @EntityGraph(attributePaths = {"beds", "amenities"})
    List<RoomType> findAllByDeletedAtIsNullOrderBySortOrderAscNameAsc();

    @EntityGraph(attributePaths = {"beds", "amenities"})
    Optional<RoomType> findByCodeIgnoreCaseAndDeletedAtIsNull(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);
}
