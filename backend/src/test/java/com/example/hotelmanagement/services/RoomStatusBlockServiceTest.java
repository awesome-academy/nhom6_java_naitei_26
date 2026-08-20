package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.room.RoomOperationalStatusUpdateRequest;
import com.example.hotelmanagement.dto.roomstatusblock.RoomStatusBlockCreateRequest;
import com.example.hotelmanagement.dto.roomstatusblock.RoomStatusBlockExtendRequest;
import com.example.hotelmanagement.entity.Room;
import com.example.hotelmanagement.entity.RoomStatusBlock;
import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
import com.example.hotelmanagement.entity.enums.RoomBlockType;
import com.example.hotelmanagement.entity.enums.RoomOperationalStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.exceptions.RoomStatusConflictException;
import com.example.hotelmanagement.repositories.BookingRoomRepository;
import com.example.hotelmanagement.repositories.RoomRepository;
import com.example.hotelmanagement.repositories.RoomStatusBlockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomStatusBlockServiceTest {

    private static final LocalDate START_DATE = LocalDate.of(2026, 9, 10);
    private static final LocalDate END_DATE = LocalDate.of(2026, 9, 12);
    private static final Set<BookingRoomStatus> EFFECTIVE_STATUSES = Set.of(
            BookingRoomStatus.RESERVED,
            BookingRoomStatus.OCCUPIED
    );

    @Mock
    private RoomStatusBlockRepository roomStatusBlockRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private BookingRoomRepository bookingRoomRepository;

    private RoomStatusBlockService service;
    private Room room;

    @BeforeEach
    void setUp() {
        service = new RoomStatusBlockService(
                roomStatusBlockRepository,
                roomRepository,
                bookingRoomRepository
        );
        room = Room.builder()
                .roomNumber("A101")
                .isActive(true)
                .operationalStatus(RoomOperationalStatus.ACTIVE)
                .build();
        room.setId(10L);
    }

    @Test
    void createBlockNormalizesRoomAndBuildsPublicResponse() {
        when(roomRepository.findOperationalForUpdateByRoomNumber("A101")).thenReturn(Optional.of(room));
        when(roomStatusBlockRepository.existsOverlappingBlock(10L, START_DATE, END_DATE))
                .thenReturn(false);
        when(bookingRoomRepository.existsOverlappingBooking(
                10L, EFFECTIVE_STATUSES, START_DATE, END_DATE
        )).thenReturn(false);
        when(roomStatusBlockRepository.saveAndFlush(any(RoomStatusBlock.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createBlock(
                new RoomStatusBlockCreateRequest(
                        " a101 ",
                        RoomBlockType.MAINTENANCE,
                        START_DATE,
                        END_DATE,
                        "  Replace air conditioner  "
                ),
                7L
        );

        ArgumentCaptor<RoomStatusBlock> captor = ArgumentCaptor.forClass(RoomStatusBlock.class);
        verify(roomStatusBlockRepository).saveAndFlush(captor.capture());
        assertNotNull(UUID.fromString(response.publicId()));
        assertEquals("A101", response.roomNumber());
        assertEquals("Replace air conditioner", response.reason());
        assertEquals(7L, captor.getValue().getCreatedBy());
    }

    @Test
    void createBlockConvertsBlankReasonToNull() {
        stubAvailableRoomAndRange();
        when(roomStatusBlockRepository.saveAndFlush(any(RoomStatusBlock.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createBlock(createRequest("   "), 7L);

        assertNull(response.reason());
    }

    @Test
    void rejectsInvalidHalfOpenDateRange() {
        RoomStatusBlockCreateRequest request = new RoomStatusBlockCreateRequest(
                "A101", RoomBlockType.MAINTENANCE, START_DATE, START_DATE, null
        );

        assertThrows(BusinessValidationException.class, () -> service.createBlock(request, 7L));
        verify(roomRepository, never()).findOperationalForUpdateByRoomNumber(any());
    }

    @Test
    void rejectsInactiveOrDeletedRoomAsNotFound() {
        room.setIsActive(false);
        when(roomRepository.findOperationalForUpdateByRoomNumber("A101")).thenReturn(Optional.of(room));

        assertThrows(ResourceNotFoundException.class, () -> service.createBlock(createRequest(null), 7L));
    }

    @Test
    void rejectsOverlappingBlockBeforeCheckingBooking() {
        when(roomRepository.findOperationalForUpdateByRoomNumber("A101")).thenReturn(Optional.of(room));
        when(roomStatusBlockRepository.existsOverlappingBlock(10L, START_DATE, END_DATE))
                .thenReturn(true);

        assertThrows(RoomStatusConflictException.class, () -> service.createBlock(createRequest(null), 7L));
        verify(bookingRoomRepository, never()).existsOverlappingBooking(any(), any(), any(), any());
    }

    @Test
    void rejectsOverlappingEffectiveBooking() {
        when(roomRepository.findOperationalForUpdateByRoomNumber("A101")).thenReturn(Optional.of(room));
        when(bookingRoomRepository.existsOverlappingBooking(
                10L, EFFECTIVE_STATUSES, START_DATE, END_DATE
        )).thenReturn(true);

        assertThrows(RoomStatusConflictException.class, () -> service.createBlock(createRequest(null), 7L));
    }

    @Test
    void adjacentBlockIsAllowedWhenRepositoryReportsNoHalfOpenOverlap() {
        stubAvailableRoomAndRange();
        when(roomStatusBlockRepository.saveAndFlush(any(RoomStatusBlock.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.createBlock(createRequest(null), 7L);

        verify(roomStatusBlockRepository).saveAndFlush(any(RoomStatusBlock.class));
    }

    @Test
    void wrapsDatabaseRaceAsRoomStatusConflict() {
        stubAvailableRoomAndRange();
        when(roomStatusBlockRepository.saveAndFlush(any(RoomStatusBlock.class)))
                .thenThrow(new DataIntegrityViolationException("trigger rejected"));

        assertThrows(RoomStatusConflictException.class, () -> service.createBlock(createRequest(null), 7L));
    }

    @Test
    void extendOnlyChangesEndDateAndExcludesCurrentBlock() {
        UUID publicId = UUID.randomUUID();
        RoomStatusBlock block = existingBlock(publicId, END_DATE);
        LocalDate newEndDate = END_DATE.plusDays(2);
        when(roomStatusBlockRepository.findByPublicId(publicId.toString())).thenReturn(Optional.of(block));
        when(roomRepository.findOperationalForUpdateByRoomNumber("A101")).thenReturn(Optional.of(room));
        when(roomStatusBlockRepository.saveAndFlush(block)).thenReturn(block);

        var response = service.extendBlock(publicId, new RoomStatusBlockExtendRequest(newEndDate));

        verify(roomStatusBlockRepository).existsOverlappingBlockExcludingId(
                10L, 20L, START_DATE, newEndDate
        );
        assertEquals(newEndDate, response.endDate());
        assertEquals(RoomBlockType.MAINTENANCE, response.blockType());
    }

    @Test
    void rejectsExtendThatDoesNotIncreaseEndDate() {
        UUID publicId = UUID.randomUUID();
        RoomStatusBlock block = existingBlock(publicId, END_DATE);
        when(roomStatusBlockRepository.findByPublicId(publicId.toString())).thenReturn(Optional.of(block));
        when(roomRepository.findOperationalForUpdateByRoomNumber("A101")).thenReturn(Optional.of(room));

        assertThrows(
                BusinessValidationException.class,
                () -> service.extendBlock(publicId, new RoomStatusBlockExtendRequest(END_DATE))
        );
    }

    @Test
    void hardDeletesExistingBlockAfterLockingRoom() {
        UUID publicId = UUID.randomUUID();
        RoomStatusBlock block = existingBlock(publicId, END_DATE);
        when(roomStatusBlockRepository.findByPublicId(publicId.toString())).thenReturn(Optional.of(block));
        when(roomRepository.findOperationalForUpdateByRoomNumber("A101")).thenReturn(Optional.of(room));

        service.deleteBlock(publicId);

        verify(roomStatusBlockRepository).delete(block);
        verify(roomStatusBlockRepository).flush();
    }

    @Test
    void listUsesValidatedHalfOpenRange() {
        UUID publicId = UUID.randomUUID();
        RoomStatusBlock block = existingBlock(publicId, END_DATE);
        when(roomStatusBlockRepository.findOverlappingDateRange(START_DATE, END_DATE))
                .thenReturn(List.of(block));

        var result = service.getBlocks(START_DATE, END_DATE);

        assertEquals(List.of(publicId.toString()), result.stream().map(item -> item.publicId()).toList());
    }

    @Test
    void operationalStatusTransitionIsIdempotent() {
        when(roomRepository.findOperationalForUpdateByRoomNumber("A101")).thenReturn(Optional.of(room));

        var response = service.updateOperationalStatus(
                "a101",
                new RoomOperationalStatusUpdateRequest(RoomOperationalStatus.ACTIVE)
        );

        assertEquals(RoomOperationalStatus.ACTIVE, response.operationalStatus());
        verify(bookingRoomRepository, never()).existsByRoomIdAndStatusIn(any(), any());
        verify(roomRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsMovingOutOfActiveWithEffectiveBooking() {
        when(roomRepository.findOperationalForUpdateByRoomNumber("A101")).thenReturn(Optional.of(room));
        when(bookingRoomRepository.existsByRoomIdAndStatusIn(10L, EFFECTIVE_STATUSES)).thenReturn(true);

        assertThrows(
                RoomStatusConflictException.class,
                () -> service.updateOperationalStatus(
                        "A101",
                        new RoomOperationalStatusUpdateRequest(RoomOperationalStatus.MAINTENANCE)
                )
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = RoomOperationalStatus.class,
            names = {"MAINTENANCE", "OUT_OF_SERVICE", "RENOVATION"}
    )
    void movesFromActiveToEveryNonActiveStatusWhenThereIsNoBooking(
            RoomOperationalStatus targetStatus
    ) {
        when(roomRepository.findOperationalForUpdateByRoomNumber("A101")).thenReturn(Optional.of(room));
        when(roomRepository.saveAndFlush(room)).thenReturn(room);

        var response = service.updateOperationalStatus(
                "A101",
                new RoomOperationalStatusUpdateRequest(targetStatus)
        );

        assertEquals(targetStatus, response.operationalStatus());
        verify(bookingRoomRepository).existsByRoomIdAndStatusIn(10L, EFFECTIVE_STATUSES);
    }

    @Test
    void movingBackToActiveAlwaysSucceeds() {
        room.setOperationalStatus(RoomOperationalStatus.RENOVATION);
        when(roomRepository.findOperationalForUpdateByRoomNumber("A101")).thenReturn(Optional.of(room));
        when(roomRepository.saveAndFlush(room)).thenReturn(room);

        var response = service.updateOperationalStatus(
                "A101",
                new RoomOperationalStatusUpdateRequest(RoomOperationalStatus.ACTIVE)
        );

        assertEquals(RoomOperationalStatus.ACTIVE, response.operationalStatus());
        verify(bookingRoomRepository, never()).existsByRoomIdAndStatusIn(any(), any());
    }

    @Test
    void directTransitionBetweenNonActiveStatusesIsAllowedWithoutBooking() {
        room.setOperationalStatus(RoomOperationalStatus.MAINTENANCE);
        when(roomRepository.findOperationalForUpdateByRoomNumber("A101")).thenReturn(Optional.of(room));
        when(roomRepository.saveAndFlush(room)).thenReturn(room);

        var response = service.updateOperationalStatus(
                "A101",
                new RoomOperationalStatusUpdateRequest(RoomOperationalStatus.OUT_OF_SERVICE)
        );

        assertEquals(RoomOperationalStatus.OUT_OF_SERVICE, response.operationalStatus());
        verify(bookingRoomRepository, never()).existsByRoomIdAndStatusIn(any(), any());
    }

    private RoomStatusBlockCreateRequest createRequest(String reason) {
        return new RoomStatusBlockCreateRequest(
                "A101",
                RoomBlockType.MAINTENANCE,
                START_DATE,
                END_DATE,
                reason
        );
    }

    private void stubAvailableRoomAndRange() {
        when(roomRepository.findOperationalForUpdateByRoomNumber("A101")).thenReturn(Optional.of(room));
        when(roomStatusBlockRepository.existsOverlappingBlock(10L, START_DATE, END_DATE))
                .thenReturn(false);
        when(bookingRoomRepository.existsOverlappingBooking(
                10L, EFFECTIVE_STATUSES, START_DATE, END_DATE
        )).thenReturn(false);
    }

    private RoomStatusBlock existingBlock(UUID publicId, LocalDate endDate) {
        RoomStatusBlock block = RoomStatusBlock.builder()
                .publicId(publicId.toString())
                .room(room)
                .blockType(RoomBlockType.MAINTENANCE)
                .startDate(START_DATE)
                .endDate(endDate)
                .reason("Maintenance")
                .createdBy(7L)
                .build();
        block.setId(20L);
        return block;
    }
}
