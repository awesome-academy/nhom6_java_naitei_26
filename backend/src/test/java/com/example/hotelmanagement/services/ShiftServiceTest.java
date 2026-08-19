package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.shift.ShiftCreateRequest;
import com.example.hotelmanagement.dto.shift.ShiftResponse;
import com.example.hotelmanagement.dto.shift.ShiftUpdateRequest;
import com.example.hotelmanagement.entity.Shift;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.repositories.ShiftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShiftServiceTest {

    @Mock
    private ShiftRepository shiftRepository;

    private ShiftService shiftService;

    @BeforeEach
    void setUp() {
        shiftService = new ShiftService(shiftRepository);
    }

    @Test
    void createShiftNormalizesCodeAndUsesDefaults() {
        ShiftCreateRequest request = new ShiftCreateRequest(
                " morning_extra ",
                " Morning extra ",
                LocalTime.of(7, 0),
                LocalTime.of(15, 0),
                null,
                null
        );
        when(shiftRepository.existsByCodeIgnoreCase("MORNING_EXTRA")).thenReturn(false);
        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShiftResponse response = shiftService.createShift(request);

        assertEquals("MORNING_EXTRA", response.code());
        assertTrue(response.isActive());
        assertFalse(response.crossesMidnight());
    }

    @Test
    void createShiftRejectsDuplicateCode() {
        ShiftCreateRequest request = new ShiftCreateRequest(
                "night",
                "Night",
                LocalTime.of(22, 0),
                LocalTime.of(6, 0),
                true,
                true
        );
        when(shiftRepository.existsByCodeIgnoreCase("NIGHT")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> shiftService.createShift(request));
        verify(shiftRepository, never()).save(any(Shift.class));
    }

    @Test
    void createShiftRejectsInconsistentMidnightFlag() {
        ShiftCreateRequest request = new ShiftCreateRequest(
                "night",
                "Night",
                LocalTime.of(22, 0),
                LocalTime.of(6, 0),
                false,
                true
        );
        when(shiftRepository.existsByCodeIgnoreCase("NIGHT")).thenReturn(false);

        assertThrows(BusinessValidationException.class, () -> shiftService.createShift(request));
        verify(shiftRepository, never()).save(any(Shift.class));
    }

    @Test
    void updateShiftDoesNotRecalculateExistingAssignments() {
        Shift shift = createShift("MORNING", LocalTime.of(6, 0), LocalTime.of(14, 0), false);
        when(shiftRepository.findByCodeIgnoreCase("MORNING")).thenReturn(Optional.of(shift));
        when(shiftRepository.save(shift)).thenReturn(shift);

        ShiftResponse response = shiftService.updateShift(
                "morning",
                new ShiftUpdateRequest(
                        "Updated morning",
                        LocalTime.of(7, 0),
                        LocalTime.of(15, 0),
                        false,
                        true
                )
        );

        assertEquals(LocalTime.of(7, 0), response.startTime());
        ArgumentCaptor<Shift> captor = ArgumentCaptor.forClass(Shift.class);
        verify(shiftRepository).save(captor.capture());
        assertTrue(captor.getValue().getShiftAssignments().isEmpty());
    }

    @Test
    void deleteShiftDeactivatesInsteadOfDeleting() {
        Shift shift = createShift("MORNING", LocalTime.of(6, 0), LocalTime.of(14, 0), false);
        when(shiftRepository.findByCodeIgnoreCase("MORNING")).thenReturn(Optional.of(shift));

        shiftService.deleteShift("morning");

        assertFalse(shift.getIsActive());
        verify(shiftRepository).save(shift);
        verify(shiftRepository, never()).delete(any(Shift.class));
    }

    private Shift createShift(String code, LocalTime startTime, LocalTime endTime, boolean crossesMidnight) {
        return Shift.builder()
                .code(code)
                .name(code)
                .startTime(startTime)
                .endTime(endTime)
                .crossesMidnight(crossesMidnight)
                .isActive(true)
                .build();
    }
}
