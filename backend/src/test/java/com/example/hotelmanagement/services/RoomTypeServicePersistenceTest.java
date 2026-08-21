package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.roomtype.RoomTypeBedRequest;
import com.example.hotelmanagement.dto.roomtype.RoomTypeBedsRequest;
import com.example.hotelmanagement.entity.RoomType;
import com.example.hotelmanagement.entity.RoomTypeBed;
import com.example.hotelmanagement.entity.enums.BedType;
import com.example.hotelmanagement.repositories.RoomTypeRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DataJpaTest
@ActiveProfiles("test")
@Import(RoomTypeService.class)
class RoomTypeServicePersistenceTest {

    @Autowired
    private RoomTypeService roomTypeService;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private EntityManager entityManager;

    @MockBean
    private SlugService slugService;

    @MockBean
    private RoomTypeImageService roomTypeImageService;

    @Test
    void replacingBedsFlushesWithoutViolatingUniqueRoomTypeBedConstraint() {
        when(roomTypeImageService.getImageResponses(any())).thenReturn(List.of());
        RoomType roomType = RoomType.builder()
                .code("DLX")
                .name("Deluxe")
                .slug("deluxe")
                .bedCount(1)
                .maxOccupancy(2)
                .maxAdults(2)
                .maxChildren(0)
                .basePrice(new BigDecimal("1500000.00"))
                .currency("VND")
                .isActive(true)
                .sortOrder(10)
                .build();
        roomType.getBeds().add(RoomTypeBed.builder()
                .roomType(roomType)
                .bedType(BedType.QUEEN)
                .quantity(1)
                .build());
        roomTypeRepository.saveAndFlush(roomType);
        entityManager.clear();

        roomTypeService.replaceRoomTypeBeds("DLX", new RoomTypeBedsRequest(List.of(
                new RoomTypeBedRequest(BedType.QUEEN, 2),
                new RoomTypeBedRequest(BedType.KING, 1)
        )));
        entityManager.clear();

        RoomType persisted = roomTypeRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("DLX")
                .orElseThrow();
        assertEquals(3, persisted.getBedCount());
        assertEquals(2, persisted.getBeds().size());
        assertEquals(
                2,
                persisted.getBeds().stream()
                        .filter(bed -> bed.getBedType() == BedType.QUEEN)
                        .findFirst()
                        .orElseThrow()
                        .getQuantity()
        );
    }
}
