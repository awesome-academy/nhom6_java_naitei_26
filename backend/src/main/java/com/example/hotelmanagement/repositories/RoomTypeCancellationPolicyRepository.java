package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.RoomTypeCancellationPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomTypeCancellationPolicyRepository extends JpaRepository<RoomTypeCancellationPolicy, Long> {

    boolean existsByCancellationPolicy_CodeIgnoreCaseAndRoomType_DeletedAtIsNullAndRoomType_IsActiveTrue(
            String code
    );
}
