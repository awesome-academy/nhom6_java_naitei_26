package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.RoomImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomImageRepository extends JpaRepository<RoomImage, Long> {

    long countByRoomId(Long roomId);

    boolean existsByRoomIdAndStorageKey(Long roomId, String storageKey);
}
