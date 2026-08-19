package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.Amenity;
import com.example.hotelmanagement.entity.Room;
import com.example.hotelmanagement.entity.RoomType;
import com.example.hotelmanagement.entity.enums.RoomView;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.Set;

public final class RoomSpecifications {

    private RoomSpecifications() {
    }

    public static Specification<Room> matchesFilters(
            String roomTypeCode,
            RoomView viewType,
            Integer floor,
            Set<String> amenityCodes
    ) {
        Specification<Room> specification = notDeleted();
        if (roomTypeCode != null) {
            specification = specification.and(hasRoomTypeCode(roomTypeCode));
        }
        if (viewType != null) {
            specification = specification.and(hasViewType(viewType));
        }
        if (floor != null) {
            specification = specification.and(hasFloor(floor));
        }
        for (String amenityCode : amenityCodes) {
            specification = specification.and(hasEffectiveAmenity(amenityCode));
        }
        return specification;
    }

    private static Specification<Room> notDeleted() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.isNull(root.get("deletedAt"));
    }

    private static Specification<Room> hasRoomTypeCode(String roomTypeCode) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(
                criteriaBuilder.upper(root.get("roomType").get("code")),
                roomTypeCode
        );
    }

    private static Specification<Room> hasViewType(RoomView viewType) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("viewType"), viewType);
    }

    private static Specification<Room> hasFloor(Integer floor) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("floor"), floor);
    }

    private static Specification<Room> hasEffectiveAmenity(String amenityCode) {
        return (root, query, criteriaBuilder) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<Room> correlatedRoom = subquery.correlate(root);
            Join<Room, Amenity> roomAmenities = correlatedRoom.join("amenities", JoinType.LEFT);
            Join<Room, RoomType> roomType = correlatedRoom.join("roomType", JoinType.INNER);
            Join<RoomType, Amenity> roomTypeAmenities = roomType.join("amenities", JoinType.LEFT);

            subquery.select(criteriaBuilder.literal(1L));
            subquery.where(criteriaBuilder.or(
                    criteriaBuilder.equal(criteriaBuilder.upper(roomAmenities.get("code")), amenityCode),
                    criteriaBuilder.equal(criteriaBuilder.upper(roomTypeAmenities.get("code")), amenityCode)
            ));
            return criteriaBuilder.exists(subquery);
        };
    }
}
