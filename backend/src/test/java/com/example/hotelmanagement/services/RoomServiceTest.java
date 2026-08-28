package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.room.HousekeepingStatusUpdateRequest;
import com.example.hotelmanagement.dto.room.RoomCreateRequest;
import com.example.hotelmanagement.dto.room.RoomUpdateRequest;
import com.example.hotelmanagement.entity.Amenity;
import com.example.hotelmanagement.entity.Room;
import com.example.hotelmanagement.entity.RoomType;
import com.example.hotelmanagement.entity.enums.AmenityCategory;
import com.example.hotelmanagement.entity.enums.HousekeepingStatus;
import com.example.hotelmanagement.entity.enums.RoomOperationalStatus;
import com.example.hotelmanagement.entity.enums.RoomView;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.repositories.AmenityRepository;
import com.example.hotelmanagement.repositories.RoomRepository;
import com.example.hotelmanagement.repositories.RoomTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;
    @Mock
    private RoomTypeRepository roomTypeRepository;
    @Mock
    private AmenityRepository amenityRepository;
    @Mock
    private RoomImageService roomImageService;

    private RoomService roomService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-19T08:30:00Z"), ZoneOffset.UTC);
        roomService = new RoomService(
                roomRepository,
                roomTypeRepository,
                amenityRepository,
                roomImageService,
                clock
        );
    }

    @Test
    void createRoomNormalizesNumberAndAppliesDefaults() {
        RoomType roomType = createRoomType("DLX", true);
        RoomCreateRequest request = new RoomCreateRequest(
                " a-101 ", "dlx", null, 1, new BigDecimal("1200000.00")
        );
        when(roomRepository.existsByRoomNumberIgnoreCaseAndDeletedAtIsNull("A-101"))
                .thenReturn(false);
        when(roomTypeRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("DLX"))
                .thenReturn(Optional.of(roomType));
        when(roomRepository.saveAndFlush(any(Room.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = roomService.createRoom(request, 42L);

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).saveAndFlush(captor.capture());
        Room savedRoom = captor.getValue();
        assertEquals("A-101", savedRoom.getRoomNumber());
        assertEquals(RoomView.NONE, savedRoom.getViewType());
        assertEquals(RoomOperationalStatus.ACTIVE, savedRoom.getOperationalStatus());
        assertEquals(HousekeepingStatus.CLEAN, savedRoom.getHousekeepingStatus());
        assertTrue(savedRoom.getIsActive());
        assertEquals(42L, savedRoom.getCreatedBy());
        assertEquals("A-101", response.roomNumber());
    }

    @Test
    void createRoomKeepsBlankPriceOverrideAsNullToInheritRoomTypePrice() {
        RoomType roomType = createRoomType("DLX", true);
        RoomCreateRequest request = new RoomCreateRequest("A-102", "DLX", RoomView.NONE, 1, null);
        when(roomRepository.existsByRoomNumberIgnoreCaseAndDeletedAtIsNull("A-102"))
                .thenReturn(false);
        when(roomTypeRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("DLX"))
                .thenReturn(Optional.of(roomType));
        when(roomRepository.saveAndFlush(any(Room.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        roomService.createRoom(request, 42L);

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).saveAndFlush(captor.capture());
        assertNull(captor.getValue().getPriceOverride());
    }

    @Test
    void createRoomRejectsDuplicateActiveRoomNumber() {
        RoomCreateRequest request = new RoomCreateRequest("A101", "DLX", RoomView.SEA, 1, null);
        when(roomRepository.existsByRoomNumberIgnoreCaseAndDeletedAtIsNull("A101"))
                .thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> roomService.createRoom(request, 42L));

        verify(roomTypeRepository, never()).findByCodeIgnoreCaseAndDeletedAtIsNull(any());
        verify(roomRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRoomRejectsInactiveRoomType() {
        RoomCreateRequest request = new RoomCreateRequest("A101", "DLX", RoomView.SEA, 1, null);
        when(roomTypeRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("DLX"))
                .thenReturn(Optional.of(createRoomType("DLX", false)));

        assertThrows(BusinessValidationException.class, () -> roomService.createRoom(request, 42L));

        verify(roomRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateRoomAllowsKeepingAnInactiveCurrentRoomType() {
        Room room = createRoom(HousekeepingStatus.CLEAN);
        room.getRoomType().setIsActive(false);
        when(roomRepository.findByRoomNumberIgnoreCaseAndDeletedAtIsNull("A101"))
                .thenReturn(Optional.of(room));
        when(roomRepository.saveAndFlush(room)).thenReturn(room);

        roomService.updateRoom(
                "a101",
                new RoomUpdateRequest("dlx", RoomView.CITY, 5, new BigDecimal("999.99"))
        );

        assertEquals(RoomView.CITY, room.getViewType());
        assertEquals(5, room.getFloor());
        verify(roomTypeRepository, never()).findByCodeIgnoreCaseAndDeletedAtIsNull(any());
    }

    @Test
    void updateRoomReassignsOnlyToAnActiveRoomType() {
        Room room = createRoom(HousekeepingStatus.CLEAN);
        RoomType suite = createRoomType("SUITE", true);
        when(roomRepository.findByRoomNumberIgnoreCaseAndDeletedAtIsNull("A101"))
                .thenReturn(Optional.of(room));
        when(roomTypeRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("SUITE"))
                .thenReturn(Optional.of(suite));
        when(roomRepository.saveAndFlush(room)).thenReturn(room);

        roomService.updateRoom("A101", new RoomUpdateRequest("suite", RoomView.SEA, 2, null));

        assertEquals("SUITE", room.getRoomType().getCode());
    }

    @Test
    void deleteRoomUsesSoftDeleteWithUtcTimestamp() {
        Room room = createRoom(HousekeepingStatus.CLEAN);
        when(roomRepository.findByRoomNumberIgnoreCaseAndDeletedAtIsNull("A101"))
                .thenReturn(Optional.of(room));

        roomService.deleteRoom("a101");

        assertFalse(room.getIsActive());
        assertNotNull(room.getDeletedAt());
        assertEquals(Instant.parse("2026-08-19T08:30:00Z"), room.getDeletedAt().toInstant());
        verify(roomRepository).saveAndFlush(room);
    }

    @Test
    void housekeepingAllowsExactCycleAndSameStatusRetry() {
        Room room = createRoom(HousekeepingStatus.CLEAN);
        when(roomRepository.findByRoomNumberIgnoreCaseAndDeletedAtIsNull("A101"))
                .thenReturn(Optional.of(room));
        when(roomRepository.saveAndFlush(room)).thenReturn(room);

        roomService.updateHousekeepingStatus("A101", requestStatus(HousekeepingStatus.CLEAN));
        verify(roomRepository, never()).saveAndFlush(room);

        roomService.updateHousekeepingStatus("A101", requestStatus(HousekeepingStatus.DIRTY));
        roomService.updateHousekeepingStatus("A101", requestStatus(HousekeepingStatus.CLEANING));
        roomService.updateHousekeepingStatus("A101", requestStatus(HousekeepingStatus.CLEAN));

        assertEquals(HousekeepingStatus.CLEAN, room.getHousekeepingStatus());
    }

    @Test
    void housekeepingRejectsSkippedTransitions() {
        Room room = createRoom(HousekeepingStatus.CLEAN);
        when(roomRepository.findByRoomNumberIgnoreCaseAndDeletedAtIsNull("A101"))
                .thenReturn(Optional.of(room));

        assertThrows(
                BusinessValidationException.class,
                () -> roomService.updateHousekeepingStatus("A101", requestStatus(HousekeepingStatus.CLEANING))
        );
    }

    @Test
    void getRoomsRejectsUnknownOrNonFilterableAmenities() {
        Amenity internalAmenity = createAmenity("INTERNAL", false);
        when(amenityRepository.findAllByCodeIn(anyCollection())).thenReturn(List.of(internalAmenity));

        assertThrows(
                BusinessValidationException.class,
                () -> roomService.getRooms(null, null, null, List.of("internal", "missing"))
        );

        verify(roomRepository, never()).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    void getRoomsNormalizesAndDelegatesAllFilters() {
        Amenity wifi = createAmenity("WIFI", true);
        Amenity balcony = createAmenity("BALCONY", true);
        when(amenityRepository.findAllByCodeIn(anyCollection())).thenReturn(List.of(wifi, balcony));
        when(roomRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        roomService.getRooms(" dlx ", RoomView.SEA, 3, List.of("wifi", "BALCONY"));

        verify(roomRepository).findAll(any(Specification.class), any(Sort.class));
    }

    private HousekeepingStatusUpdateRequest requestStatus(HousekeepingStatus status) {
        return new HousekeepingStatusUpdateRequest(status);
    }

    private Room createRoom(HousekeepingStatus housekeepingStatus) {
        return Room.builder()
                .roomNumber("A101")
                .roomType(createRoomType("DLX", true))
                .viewType(RoomView.SEA)
                .floor(1)
                .operationalStatus(RoomOperationalStatus.ACTIVE)
                .housekeepingStatus(housekeepingStatus)
                .isActive(true)
                .build();
    }

    private RoomType createRoomType(String code, boolean active) {
        return RoomType.builder()
                .code(code)
                .name(code + " Room")
                .slug(code.toLowerCase())
                .bedCount(1)
                .maxOccupancy(2)
                .maxAdults(2)
                .maxChildren(0)
                .basePrice(new BigDecimal("1000.00"))
                .currency("VND")
                .isActive(active)
                .build();
    }

    private Amenity createAmenity(String code, boolean filterable) {
        return Amenity.builder()
                .code(code)
                .name(code)
                .category(AmenityCategory.ROOM)
                .isFilterable(filterable)
                .sortOrder(0)
                .build();
    }
}
