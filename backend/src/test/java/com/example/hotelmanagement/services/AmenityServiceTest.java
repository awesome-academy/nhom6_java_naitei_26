package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.amenity.AmenityCreateRequest;
import com.example.hotelmanagement.dto.amenity.AmenityDetailResponse;
import com.example.hotelmanagement.dto.amenity.AmenityUpdateRequest;
import com.example.hotelmanagement.entity.Amenity;
import com.example.hotelmanagement.entity.enums.AmenityCategory;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.AmenityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AmenityServiceTest {

    @Mock
    private AmenityRepository amenityRepository;

    private AmenityService amenityService;

    @BeforeEach
    void setUp() {
        amenityService = new AmenityService(amenityRepository);
    }

    @Test
    void createAmenityNormalizesFieldsAndAppliesDefaults() {
        AmenityCreateRequest request = new AmenityCreateRequest(
                " wifi ",
                " Wi-Fi ",
                "   ",
                AmenityCategory.TECH,
                null,
                null
        );
        when(amenityRepository.existsByCodeIgnoreCase("WIFI")).thenReturn(false);
        when(amenityRepository.saveAndFlush(any(Amenity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AmenityDetailResponse response = amenityService.createAmenity(request);

        assertEquals("WIFI", response.code());
        assertEquals("Wi-Fi", response.name());
        assertNull(response.icon());
        assertTrue(response.isFilterable());
        assertEquals(0, response.sortOrder());

        ArgumentCaptor<Amenity> amenityCaptor = ArgumentCaptor.forClass(Amenity.class);
        verify(amenityRepository).saveAndFlush(amenityCaptor.capture());
        assertEquals("WIFI", amenityCaptor.getValue().getCode());
    }

    @Test
    void createAmenityRejectsDuplicateCodeIgnoringCase() {
        AmenityCreateRequest request = new AmenityCreateRequest(
                "wifi",
                "Wi-Fi",
                "wifi",
                AmenityCategory.TECH,
                true,
                10
        );
        when(amenityRepository.existsByCodeIgnoreCase("WIFI")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> amenityService.createAmenity(request));

        verify(amenityRepository, never()).saveAndFlush(any(Amenity.class));
    }

    @Test
    void updateAmenityKeepsCodeAndUpdatesFilterConfiguration() {
        Amenity amenity = createAmenity("WIFI", true, 10);
        AmenityUpdateRequest request = new AmenityUpdateRequest(
                " Wireless internet ",
                " network-wifi ",
                AmenityCategory.SERVICE,
                false,
                25
        );
        when(amenityRepository.findByCodeIgnoreCase("WIFI")).thenReturn(Optional.of(amenity));
        when(amenityRepository.saveAndFlush(amenity)).thenReturn(amenity);

        AmenityDetailResponse response = amenityService.updateAmenity(" wifi ", request);

        assertEquals("WIFI", response.code());
        assertEquals("Wireless internet", response.name());
        assertEquals("network-wifi", response.icon());
        assertEquals(AmenityCategory.SERVICE, response.category());
        assertFalse(response.isFilterable());
        assertEquals(25, response.sortOrder());
    }

    @Test
    void getFilterOptionsUsesOnlyFilterableRepositoryQuery() {
        Amenity amenity = createAmenity("WIFI", true, 10);
        when(amenityRepository.findAllByIsFilterableTrueOrderByCategoryAscSortOrderAscNameAscCodeAsc())
                .thenReturn(List.of(amenity));

        var options = amenityService.getFilterOptions();

        assertEquals(1, options.size());
        assertEquals("WIFI", options.getFirst().code());
        assertEquals(AmenityCategory.TECH, options.getFirst().category());
    }

    @Test
    void deleteAmenityHardDeletesExistingEntity() {
        Amenity amenity = createAmenity("WIFI", true, 10);
        when(amenityRepository.findByCodeIgnoreCase("WIFI")).thenReturn(Optional.of(amenity));

        amenityService.deleteAmenity("wifi");

        verify(amenityRepository).delete(amenity);
        verify(amenityRepository).flush();
    }

    @Test
    void getAmenityRejectsBlankCodeBeforeRepositoryLookup() {
        assertThrows(BusinessValidationException.class, () -> amenityService.getAmenity("   "));

        verify(amenityRepository, never()).findByCodeIgnoreCase(any());
    }

    @Test
    void getAmenityRejectsUnsupportedCodeCharactersBeforeRepositoryLookup() {
        assertThrows(BusinessValidationException.class, () -> amenityService.getAmenity("WIFI FREE"));

        verify(amenityRepository, never()).findByCodeIgnoreCase(any());
    }

    @Test
    void getAmenityReturnsNotFoundForUnknownCode() {
        when(amenityRepository.findByCodeIgnoreCase("UNKNOWN")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> amenityService.getAmenity("unknown"));
    }

    private Amenity createAmenity(String code, boolean filterable, int sortOrder) {
        return Amenity.builder()
                .code(code)
                .name("Wi-Fi")
                .icon("wifi")
                .category(AmenityCategory.TECH)
                .isFilterable(filterable)
                .sortOrder(sortOrder)
                .build();
    }
}
