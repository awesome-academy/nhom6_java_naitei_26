package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.Amenity;
import com.example.hotelmanagement.entity.Room;
import com.example.hotelmanagement.entity.RoomType;
import com.example.hotelmanagement.entity.enums.AmenityCategory;
import com.example.hotelmanagement.entity.enums.RoomView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class RoomRepositoryTest {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private AmenityRepository amenityRepository;

    @Test
    void effectiveAmenityFilterUsesRoomTypeUnionRoomAmenitiesWithAndSemantics() {
        Amenity wifi = amenityRepository.save(amenity("WIFI"));
        Amenity balcony = amenityRepository.save(amenity("BALCONY"));
        amenityRepository.save(amenity("SPA"));

        RoomType roomType = roomType("DLX");
        roomType.getAmenities().add(wifi);
        roomType = roomTypeRepository.saveAndFlush(roomType);

        Room matchingRoom = Room.builder()
                .roomNumber("A301")
                .roomType(roomType)
                .viewType(RoomView.SEA)
                .floor(3)
                .build();
        matchingRoom.getAmenities().add(balcony);
        roomRepository.saveAndFlush(matchingRoom);

        Room differentRoom = Room.builder()
                .roomNumber("A302")
                .roomType(roomType)
                .viewType(RoomView.CITY)
                .floor(3)
                .build();
        roomRepository.saveAndFlush(differentRoom);

        List<Room> rooms = roomRepository.findAll(
                RoomSpecifications.matchesFilters(
                        "DLX", RoomView.SEA, 3, Set.of("WIFI", "BALCONY")
                ),
                Sort.by("floor", "roomNumber")
        );
        List<Room> missingSpa = roomRepository.findAll(
                RoomSpecifications.matchesFilters(
                        "DLX", RoomView.SEA, 3, Set.of("WIFI", "BALCONY", "SPA")
                ),
                Sort.by("floor", "roomNumber")
        );

        assertEquals(List.of("A301"), rooms.stream().map(Room::getRoomNumber).toList());
        assertTrue(missingSpa.isEmpty());
        assertTrue(roomRepository.findForUpdateByRoomNumber("a301").isPresent());
    }

    private Amenity amenity(String code) {
        return Amenity.builder()
                .code(code)
                .name(code)
                .category(AmenityCategory.ROOM)
                .isFilterable(true)
                .sortOrder(0)
                .build();
    }

    private RoomType roomType(String code) {
        return RoomType.builder()
                .code(code)
                .name("Deluxe")
                .slug("deluxe")
                .bedCount(1)
                .maxOccupancy(2)
                .maxAdults(2)
                .maxChildren(0)
                .basePrice(new BigDecimal("1000.00"))
                .currency("VND")
                .isActive(true)
                .build();
    }
}
