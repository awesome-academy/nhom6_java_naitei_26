package com.example.hotelmanagement.services;

import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.repositories.RoomTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlugServiceTest {

    @Mock
    private RoomTypeRepository roomTypeRepository;

    private SlugService slugService;

    @BeforeEach
    void setUp() {
        slugService = new SlugService(roomTypeRepository);
    }

    @Test
    void generateUniqueSlugRemovesVietnameseDiacritics() {
        when(roomTypeRepository.existsBySlug("phong-doi-huong-bien")).thenReturn(false);

        String slug = slugService.generateUniqueSlug("Phòng Đôi Hướng Biển");

        assertEquals("phong-doi-huong-bien", slug);
    }

    @Test
    void generateUniqueSlugAddsNumericSuffixWhenSlugExists() {
        when(roomTypeRepository.existsBySlug("deluxe")).thenReturn(true);
        when(roomTypeRepository.existsBySlug("deluxe-2")).thenReturn(true);
        when(roomTypeRepository.existsBySlug("deluxe-3")).thenReturn(false);

        String slug = slugService.generateUniqueSlug("Deluxe");

        assertEquals("deluxe-3", slug);
    }

    @Test
    void generateUniqueSlugForUpdateExcludesCurrentRoomType() {
        when(roomTypeRepository.existsBySlugAndIdNot("suite", 42L)).thenReturn(false);

        String slug = slugService.generateUniqueSlugForUpdate("Suite", 42L);

        assertEquals("suite", slug);
    }

    @Test
    void generateUniqueSlugRejectsNameWithoutSlugCharacters() {
        assertThrows(BusinessValidationException.class, () -> slugService.generateUniqueSlug("東京"));
    }
}
