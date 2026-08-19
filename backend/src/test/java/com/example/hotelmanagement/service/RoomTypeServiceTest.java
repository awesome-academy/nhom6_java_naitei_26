package com.example.hotelmanagement.service;

import com.example.hotelmanagement.dto.roomtype.RoomTypeAmenitiesRequest;
import com.example.hotelmanagement.dto.roomtype.RoomTypeBedRequest;
import com.example.hotelmanagement.dto.roomtype.RoomTypeBedsRequest;
import com.example.hotelmanagement.dto.roomtype.RoomTypeCreateRequest;
import com.example.hotelmanagement.dto.roomtype.RoomTypeResponse;
import com.example.hotelmanagement.entity.Amenity;
import com.example.hotelmanagement.entity.RoomType;
import com.example.hotelmanagement.entity.enums.AmenityCategory;
import com.example.hotelmanagement.entity.enums.BedType;
import com.example.hotelmanagement.exception.BusinessValidationException;
import com.example.hotelmanagement.exception.ResourceNotFoundException;
import com.example.hotelmanagement.repository.AmenityRepository;
import com.example.hotelmanagement.repository.RoomTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomTypeServiceTest {

    @Mock
    private RoomTypeRepository roomTypeRepository;

    @Mock
    private AmenityRepository amenityRepository;

    @Mock
    private SlugService slugService;

    private RoomTypeService roomTypeService;

    @BeforeEach
    void setUp() {
        roomTypeService = new RoomTypeService(roomTypeRepository, amenityRepository, slugService);
    }

    @Test
    void createRoomTypeDerivesBedCountAndAssignsAmenities() {
        Amenity wifi = createAmenity("WIFI", "Wi-Fi", AmenityCategory.TECH, 10);
        Amenity airConditioner = createAmenity("AC", "Air conditioner", AmenityCategory.ROOM, 40);
        RoomTypeCreateRequest request = new RoomTypeCreateRequest(
                "dlx_ocean",
                "Deluxe Ocean",
                "Ocean-facing deluxe room",
                4,
                3,
                2,
                new BigDecimal("1800000.00"),
                null,
                new BigDecimal("300000.00"),
                new BigDecimal("42.50"),
                null,
                null,
                List.of(
                        new RoomTypeBedRequest(BedType.QUEEN, 2),
                        new RoomTypeBedRequest(BedType.SOFA_BED, 1)
                ),
                Set.of("wifi", "AC")
        );

        when(roomTypeRepository.existsByCodeIgnoreCase("DLX_OCEAN")).thenReturn(false);
        when(slugService.generateUniqueSlug("Deluxe Ocean")).thenReturn("deluxe-ocean");
        when(amenityRepository.findAllByCodeIn(anyCollection())).thenReturn(List.of(wifi, airConditioner));
        when(roomTypeRepository.save(any(RoomType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoomTypeResponse response = roomTypeService.createRoomType(request);

        assertEquals("DLX_OCEAN", response.code());
        assertEquals("deluxe-ocean", response.slug());
        assertEquals(3, response.bedCount());
        assertEquals("VND", response.currency());
        assertEquals(2, response.amenities().size());

        ArgumentCaptor<RoomType> roomTypeCaptor = ArgumentCaptor.forClass(RoomType.class);
        verify(roomTypeRepository).save(roomTypeCaptor.capture());
        assertEquals(3, roomTypeCaptor.getValue().getBedCount());
        assertEquals(2, roomTypeCaptor.getValue().getAmenities().size());
    }

    @Test
    void deleteRoomTypeUsesSoftDelete() {
        RoomType roomType = createRoomType("DLX");
        when(roomTypeRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("DLX"))
                .thenReturn(Optional.of(roomType));

        roomTypeService.deleteRoomType("dlx");

        assertFalse(roomType.getIsActive());
        assertNotNull(roomType.getDeletedAt());
        verify(roomTypeRepository).save(roomType);
    }

    @Test
    void replaceRoomTypeBedsRejectsDuplicateBedTypes() {
        RoomType roomType = createRoomType("DLX");
        RoomTypeBedsRequest request = new RoomTypeBedsRequest(List.of(
                new RoomTypeBedRequest(BedType.QUEEN, 1),
                new RoomTypeBedRequest(BedType.QUEEN, 1)
        ));
        when(roomTypeRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("DLX"))
                .thenReturn(Optional.of(roomType));

        assertThrows(
                BusinessValidationException.class,
                () -> roomTypeService.replaceRoomTypeBeds("DLX", request)
        );
        verify(roomTypeRepository, never()).save(any(RoomType.class));
    }

    @Test
    void replaceRoomTypeAmenitiesRejectsUnknownCodes() {
        RoomType roomType = createRoomType("DLX");
        when(roomTypeRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("DLX"))
                .thenReturn(Optional.of(roomType));
        when(amenityRepository.findAllByCodeIn(anyCollection())).thenReturn(List.of());

        assertThrows(
                ResourceNotFoundException.class,
                () -> roomTypeService.replaceRoomTypeAmenities(
                        "DLX",
                        new RoomTypeAmenitiesRequest(Set.of("UNKNOWN"))
                )
        );
        verify(roomTypeRepository, never()).save(any(RoomType.class));
    }

    private Amenity createAmenity(String code, String name, AmenityCategory category, int sortOrder) {
        return Amenity.builder()
                .code(code)
                .name(name)
                .category(category)
                .isFilterable(true)
                .sortOrder(sortOrder)
                .build();
    }

    private RoomType createRoomType(String code) {
        return RoomType.builder()
                .code(code)
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
    }
}
