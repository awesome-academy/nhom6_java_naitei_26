package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.RoomTypeImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomTypeImageRepository extends JpaRepository<RoomTypeImage, Long> {

    long countByRoomTypeId(Long roomTypeId);

    boolean existsByRoomTypeIdAndStorageKey(Long roomTypeId, String storageKey);
}
