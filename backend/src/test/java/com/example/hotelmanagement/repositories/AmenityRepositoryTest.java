package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.Amenity;
import com.example.hotelmanagement.entity.enums.AmenityCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@ActiveProfiles("test")
class AmenityRepositoryTest {

    @Autowired
    private AmenityRepository amenityRepository;

    @Test
    void filterOptionsExcludeNonFilterableAmenitiesAndUseConfiguredOrder() {
        amenityRepository.saveAllAndFlush(List.of(
                createAmenity("WIFI", "Wi-Fi", true, 20),
                createAmenity("TV", "Television", true, 10),
                createAmenity("INTERNAL_NOTE", "Internal note", false, 0)
        ));

        List<Amenity> filterOptions = amenityRepository
                .findAllByIsFilterableTrueOrderByCategoryAscSortOrderAscNameAscCodeAsc();

        assertEquals(List.of("TV", "WIFI"), filterOptions.stream().map(Amenity::getCode).toList());
    }

    private Amenity createAmenity(String code, String name, boolean filterable, int sortOrder) {
        return Amenity.builder()
                .code(code)
                .name(name)
                .category(AmenityCategory.TECH)
                .isFilterable(filterable)
                .sortOrder(sortOrder)
                .build();
    }
}
