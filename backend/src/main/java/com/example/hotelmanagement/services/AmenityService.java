package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.amenity.AmenityCreateRequest;
import com.example.hotelmanagement.dto.amenity.AmenityDetailResponse;
import com.example.hotelmanagement.dto.amenity.AmenityFilterOptionResponse;
import com.example.hotelmanagement.dto.amenity.AmenityUpdateRequest;
import com.example.hotelmanagement.entity.Amenity;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.AmenityRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@Validated
@Transactional
public class AmenityService {

    private static final int DEFAULT_SORT_ORDER = 0;
    private static final int MAX_CODE_LENGTH = 40;
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");

    private final AmenityRepository amenityRepository;

    public AmenityService(AmenityRepository amenityRepository) {
        this.amenityRepository = amenityRepository;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(PermissionExpressions.ROOM_READ)
    public List<AmenityDetailResponse> getAmenities() {
        return amenityRepository.findAllByOrderByCategoryAscSortOrderAscNameAscCodeAsc()
                .stream()
                .map(this::mapAmenityResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize(PermissionExpressions.ROOM_READ)
    public AmenityDetailResponse getAmenity(String code) {
        return mapAmenityResponse(getExistingAmenity(code));
    }

    @Transactional(readOnly = true)
    @PreAuthorize(PermissionExpressions.ROOM_READ)
    public List<AmenityFilterOptionResponse> getFilterOptions() {
        return amenityRepository
                .findAllByIsFilterableTrueOrderByCategoryAscSortOrderAscNameAscCodeAsc()
                .stream()
                .map(this::mapFilterOptionResponse)
                .toList();
    }

    @PreAuthorize(PermissionExpressions.ROOM_CREATE)
    public AmenityDetailResponse createAmenity(@Valid AmenityCreateRequest request) {
        String normalizedCode = normalizeCode(request.code());
        if (amenityRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new DuplicateResourceException("Amenity", "code", normalizedCode);
        }

        Amenity amenity = Amenity.builder()
                .code(normalizedCode)
                .name(request.name().strip())
                .icon(normalizeOptionalText(request.icon()))
                .category(request.category())
                .isFilterable(getValueOrDefault(request.isFilterable(), true))
                .sortOrder(getValueOrDefault(request.sortOrder(), DEFAULT_SORT_ORDER))
                .build();

        return mapAmenityResponse(amenityRepository.saveAndFlush(amenity));
    }

    @PreAuthorize(PermissionExpressions.ROOM_UPDATE)
    public AmenityDetailResponse updateAmenity(String code, @Valid AmenityUpdateRequest request) {
        Amenity amenity = getExistingAmenity(code);
        amenity.setName(request.name().strip());
        amenity.setIcon(normalizeOptionalText(request.icon()));
        amenity.setCategory(request.category());
        amenity.setIsFilterable(request.isFilterable());
        amenity.setSortOrder(request.sortOrder());

        return mapAmenityResponse(amenityRepository.saveAndFlush(amenity));
    }

    @PreAuthorize(PermissionExpressions.ROOM_DELETE)
    public void deleteAmenity(String code) {
        Amenity amenity = getExistingAmenity(code);
        amenityRepository.delete(amenity);
        amenityRepository.flush();
    }

    private Amenity getExistingAmenity(String code) {
        String normalizedCode = normalizeCode(code);
        return amenityRepository.findByCodeIgnoreCase(normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("Amenity", normalizedCode));
    }

    private AmenityDetailResponse mapAmenityResponse(Amenity amenity) {
        return new AmenityDetailResponse(
                amenity.getCode(),
                amenity.getName(),
                amenity.getIcon(),
                amenity.getCategory(),
                amenity.getIsFilterable(),
                amenity.getSortOrder(),
                amenity.getCreatedAt(),
                amenity.getUpdatedAt()
        );
    }

    private AmenityFilterOptionResponse mapFilterOptionResponse(Amenity amenity) {
        return new AmenityFilterOptionResponse(
                amenity.getCode(),
                amenity.getName(),
                amenity.getIcon(),
                amenity.getCategory(),
                amenity.getSortOrder()
        );
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessValidationException("Amenity code cannot be blank");
        }
        String normalizedCode = code.strip();
        if (normalizedCode.length() > MAX_CODE_LENGTH || !CODE_PATTERN.matcher(normalizedCode).matches()) {
            throw new BusinessValidationException(
                    "Amenity code must contain only letters, numbers, and underscores, up to 40 characters"
            );
        }
        return normalizedCode.toUpperCase(Locale.ROOT);
    }

    private String normalizeOptionalText(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private int getValueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private boolean getValueOrDefault(Boolean value, boolean defaultValue) {
        return value == null ? defaultValue : value;
    }
}
