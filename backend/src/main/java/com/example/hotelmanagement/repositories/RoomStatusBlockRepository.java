package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.RoomStatusBlock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoomStatusBlockRepository extends JpaRepository<RoomStatusBlock, Long> {

    @EntityGraph(attributePaths = "room")
    Optional<RoomStatusBlock> findByPublicId(String publicId);

    @EntityGraph(attributePaths = "room")
    @Query("""
            SELECT block FROM RoomStatusBlock block
            WHERE block.startDate < :endDate
              AND block.endDate > :startDate
              AND block.room.deletedAt IS NULL
              AND block.room.isActive = true
            ORDER BY block.startDate ASC, block.room.roomNumber ASC
            """)
    List<RoomStatusBlock> findOverlappingDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            SELECT CASE WHEN COUNT(block) > 0 THEN true ELSE false END
            FROM RoomStatusBlock block
            WHERE block.room.id = :roomId
              AND block.startDate < :endDate
              AND block.endDate > :startDate
            """)
    boolean existsOverlappingBlock(
            @Param("roomId") Long roomId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            SELECT CASE WHEN COUNT(block) > 0 THEN true ELSE false END
            FROM RoomStatusBlock block
            WHERE block.room.id = :roomId
              AND block.id <> :excludedBlockId
              AND block.startDate < :endDate
              AND block.endDate > :startDate
            """)
    boolean existsOverlappingBlockExcludingId(
            @Param("roomId") Long roomId,
            @Param("excludedBlockId") Long excludedBlockId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
