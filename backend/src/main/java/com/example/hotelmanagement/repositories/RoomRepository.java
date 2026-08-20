package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long>, JpaSpecificationExecutor<Room> {

    @EntityGraph(attributePaths = {"roomType"})
    Optional<Room> findByIdAndDeletedAtIsNull(Long id);

    @EntityGraph(attributePaths = {"roomType", "roomType.amenities", "amenities", "images"})
    Optional<Room> findByRoomNumberIgnoreCaseAndDeletedAtIsNull(String roomNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"roomType", "roomType.amenities", "amenities", "images"})
    @Query("""
            SELECT room FROM Room room
            WHERE UPPER(room.roomNumber) = UPPER(:roomNumber)
              AND room.deletedAt IS NULL
            """)
    Optional<Room> findForUpdateByRoomNumber(@Param("roomNumber") String roomNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT room FROM Room room
            WHERE UPPER(room.roomNumber) = UPPER(:roomNumber)
              AND room.deletedAt IS NULL
            """)
    Optional<Room> findOperationalForUpdateByRoomNumber(@Param("roomNumber") String roomNumber);

    boolean existsByRoomNumberIgnoreCaseAndDeletedAtIsNull(String roomNumber);

    @Override
    @EntityGraph(attributePaths = {"roomType", "roomType.amenities", "amenities", "images"})
    List<Room> findAll(Specification<Room> specification, Sort sort);
}
