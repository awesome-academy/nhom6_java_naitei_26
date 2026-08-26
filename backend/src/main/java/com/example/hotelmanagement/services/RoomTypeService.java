package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.cancellationpolicy.CancellationPolicyResponse;
import com.example.hotelmanagement.dto.cancellationpolicy.CancellationPolicyRuleResponse;
import com.example.hotelmanagement.dto.roomtype.AmenityResponse;
import com.example.hotelmanagement.dto.roomtype.RoomTypeAmenitiesRequest;
import com.example.hotelmanagement.dto.roomtype.RoomTypeBedRequest;
import com.example.hotelmanagement.dto.roomtype.RoomTypeBedResponse;
import com.example.hotelmanagement.dto.roomtype.RoomTypeBedsRequest;
import com.example.hotelmanagement.dto.roomtype.RoomTypeBookingOptionResponse;
import com.example.hotelmanagement.dto.roomtype.RoomTypeCancellationPolicyOptionResponse;
import com.example.hotelmanagement.dto.roomtype.RoomTypeCreateRequest;
import com.example.hotelmanagement.dto.roomtype.RoomTypeResponse;
import com.example.hotelmanagement.dto.roomtype.RoomTypeStatsResponse;
import com.example.hotelmanagement.dto.roomtype.RoomTypeUpdateRequest;
import com.example.hotelmanagement.entity.Amenity;
import com.example.hotelmanagement.entity.CancellationPolicy;
import com.example.hotelmanagement.entity.CancellationPolicyRule;
import com.example.hotelmanagement.entity.RoomType;
import com.example.hotelmanagement.entity.RoomTypeBed;
import com.example.hotelmanagement.entity.RoomTypeCancellationPolicy;
import com.example.hotelmanagement.entity.enums.BedType;
import com.example.hotelmanagement.entity.enums.BookingPaymentOption;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.AmenityRepository;
import com.example.hotelmanagement.repositories.CancellationPolicyRepository;
import com.example.hotelmanagement.repositories.RoomTypeRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Validated
@Transactional
public class RoomTypeService {

    private static final int MAX_TOTAL_BEDS = 10;
    private static final String DEFAULT_CURRENCY = "VND";
    private static final int DEFAULT_SORT_ORDER = 0;

    private final RoomTypeRepository roomTypeRepository;
    private final AmenityRepository amenityRepository;
    private final CancellationPolicyRepository cancellationPolicyRepository;
    private final SlugService slugService;
    private final RoomTypeImageService roomTypeImageService;

    public RoomTypeService(
            RoomTypeRepository roomTypeRepository,
            AmenityRepository amenityRepository,
            CancellationPolicyRepository cancellationPolicyRepository,
            SlugService slugService,
            RoomTypeImageService roomTypeImageService
    ) {
        this.roomTypeRepository = roomTypeRepository;
        this.amenityRepository = amenityRepository;
        this.cancellationPolicyRepository = cancellationPolicyRepository;
        this.slugService = slugService;
        this.roomTypeImageService = roomTypeImageService;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(PermissionExpressions.ROOM_READ)
    public List<RoomTypeResponse> getRoomTypes() {
        return roomTypeRepository.findAllByDeletedAtIsNullOrderBySortOrderAscNameAsc()
                .stream()
                .map(this::mapRoomTypeResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize(PermissionExpressions.ROOM_READ)
    public RoomTypeResponse getRoomType(String code) {
        return mapRoomTypeResponse(getExistingRoomType(code));
    }

    @Transactional(readOnly = true)
    @PreAuthorize(PermissionExpressions.ROOM_READ)
    public RoomTypeStatsResponse getRoomTypeStats() {
        return new RoomTypeStatsResponse(
                roomTypeRepository.count(),
                roomTypeRepository.countByDeletedAtIsNullAndIsActiveTrue(),
                roomTypeRepository.countByIsActiveFalse()
        );
    }

    @PreAuthorize(PermissionExpressions.ROOM_CREATE)
    public RoomTypeResponse createRoomType(@Valid RoomTypeCreateRequest request) {
        String normalizedCode = normalizeCode(request.code());
        if (roomTypeRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new DuplicateResourceException("Room type", "code", normalizedCode);
        }

        int maxChildren = getValueOrDefault(request.maxChildren(), 0);
        validateOccupancy(request.maxOccupancy(), request.maxAdults(), maxChildren);

        RoomType roomType = new RoomType();
        roomType.setCode(normalizedCode);
        roomType.setSlug(slugService.generateUniqueSlug(request.name()));
        applyCreateFields(roomType, request, maxChildren);
        replaceBedConfiguration(roomType, request.beds());
        replaceAmenityConfiguration(roomType, request.amenityCodes());

        return mapRoomTypeResponse(roomTypeRepository.save(roomType));
    }

    @PreAuthorize(PermissionExpressions.ROOM_UPDATE)
    public RoomTypeResponse updateRoomType(String code, @Valid RoomTypeUpdateRequest request) {
        RoomType roomType = getExistingRoomType(code);
        int maxChildren = getValueOrDefault(request.maxChildren(), 0);
        validateOccupancy(request.maxOccupancy(), request.maxAdults(), maxChildren);

        roomType.setSlug(slugService.generateUniqueSlugForUpdate(request.name(), roomType.getId()));
        applyUpdateFields(roomType, request, maxChildren);

        return mapRoomTypeResponse(roomTypeRepository.save(roomType));
    }

    @PreAuthorize(PermissionExpressions.ROOM_DELETE)
    public void deleteRoomType(String code) {
        RoomType roomType = getExistingRoomType(code);
        roomType.setIsActive(false);
        roomType.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        roomTypeRepository.save(roomType);
    }

    @PreAuthorize(PermissionExpressions.ROOM_UPDATE)
    public RoomTypeResponse replaceRoomTypeBeds(String code, @Valid RoomTypeBedsRequest request) {
        RoomType roomType = getExistingRoomType(code);
        replaceBedConfiguration(roomType, request.beds());
        return mapRoomTypeResponse(roomTypeRepository.saveAndFlush(roomType));
    }

    @PreAuthorize(PermissionExpressions.ROOM_UPDATE)
    public RoomTypeResponse replaceRoomTypeAmenities(String code, @Valid RoomTypeAmenitiesRequest request) {
        RoomType roomType = getExistingRoomType(code);
        replaceAmenityConfiguration(roomType, request.amenityCodes());
        return mapRoomTypeResponse(roomTypeRepository.saveAndFlush(roomType));
    }

    private RoomType getExistingRoomType(String code) {
        String normalizedCode = normalizeCode(code);
        return roomTypeRepository.findByCodeIgnoreCaseAndDeletedAtIsNull(normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("Room type", normalizedCode));
    }

    private void applyCreateFields(RoomType roomType, RoomTypeCreateRequest request, int maxChildren) {
        roomType.setName(request.name().strip());
        roomType.setDescription(normalizeOptionalText(request.description()));
        roomType.setMaxOccupancy(request.maxOccupancy());
        roomType.setMaxAdults(request.maxAdults());
        roomType.setMaxChildren(maxChildren);
        roomType.setBasePrice(request.basePrice());
        roomType.setCurrency(normalizeCurrency(request.currency()));
        roomType.setExtraBedPrice(request.extraBedPrice());
        roomType.setSizeSqm(request.sizeSqm());
        roomType.setIsActive(getValueOrDefault(request.isActive(), true));
        roomType.setSortOrder(getValueOrDefault(request.sortOrder(), DEFAULT_SORT_ORDER));
        roomType.setPayAtHotelEnabled(false);
        roomType.setPayAtHotelPriceAdjustmentPercent(normalizePercent(
                BigDecimal.ZERO,
                BigDecimal.ZERO
        ));
        replaceCancellationPolicyOptions(roomType, request.onlineCancellationPolicyCodes());
        validateBookingOptions(roomType);
    }

    private void applyUpdateFields(RoomType roomType, RoomTypeUpdateRequest request, int maxChildren) {
        roomType.setName(request.name().strip());
        roomType.setDescription(normalizeOptionalText(request.description()));
        roomType.setMaxOccupancy(request.maxOccupancy());
        roomType.setMaxAdults(request.maxAdults());
        roomType.setMaxChildren(maxChildren);
        roomType.setBasePrice(request.basePrice());
        roomType.setCurrency(normalizeCurrency(request.currency()));
        roomType.setExtraBedPrice(request.extraBedPrice());
        roomType.setSizeSqm(request.sizeSqm());
        roomType.setPayAtHotelEnabled(false);
        roomType.setPayAtHotelPriceAdjustmentPercent(normalizePercent(
                BigDecimal.ZERO,
                BigDecimal.ZERO
        ));
        replaceCancellationPolicyOptions(roomType, request.onlineCancellationPolicyCodes());

        if (request.isActive() != null) {
            roomType.setIsActive(request.isActive());
        }
        if (request.sortOrder() != null) {
            roomType.setSortOrder(request.sortOrder());
        }
        validateBookingOptions(roomType);
    }

    private void replaceBedConfiguration(RoomType roomType, List<RoomTypeBedRequest> bedRequests) {
        if (bedRequests == null || bedRequests.isEmpty()) {
            throw new BusinessValidationException("A room type must have at least one bed");
        }

        Map<BedType, Integer> quantitiesByType = new EnumMap<>(BedType.class);
        int totalBeds = 0;

        for (RoomTypeBedRequest bedRequest : bedRequests) {
            if (bedRequest == null || bedRequest.bedType() == null || bedRequest.quantity() == null) {
                throw new BusinessValidationException("Bed type and quantity are required");
            }
            if (bedRequest.quantity() < 1 || bedRequest.quantity() > MAX_TOTAL_BEDS) {
                throw new BusinessValidationException("Bed quantity must be between 1 and 10");
            }
            if (quantitiesByType.putIfAbsent(bedRequest.bedType(), bedRequest.quantity()) != null) {
                throw new BusinessValidationException("A bed type can appear only once");
            }
            totalBeds += bedRequest.quantity();
        }

        if (totalBeds < 1 || totalBeds > MAX_TOTAL_BEDS) {
            throw new BusinessValidationException("Total bed count must be between 1 and 10");
        }

        Map<BedType, RoomTypeBed> existingBeds = roomType.getBeds().stream()
                .collect(Collectors.toMap(
                        RoomTypeBed::getBedType,
                        bed -> bed,
                        (first, duplicate) -> first,
                        () -> new EnumMap<>(BedType.class)
                ));
        roomType.getBeds().removeIf(bed -> !quantitiesByType.containsKey(bed.getBedType()));
        quantitiesByType.forEach((bedType, quantity) -> {
            RoomTypeBed existingBed = existingBeds.get(bedType);
            if (existingBed != null) {
                existingBed.setQuantity(quantity);
                return;
            }
            roomType.getBeds().add(
                    RoomTypeBed.builder()
                            .roomType(roomType)
                            .bedType(bedType)
                            .quantity(quantity)
                            .build()
            );
        });
        roomType.setBedCount(totalBeds);
    }

    private void replaceAmenityConfiguration(RoomType roomType, Set<String> amenityCodes) {
        Set<Amenity> amenities = resolveAmenities(amenityCodes);
        Set<String> requestedCodes = amenities.stream()
                .map(Amenity::getCode)
                .map(this::normalizeCode)
                .collect(Collectors.toSet());
        roomType.getAmenities().removeIf(
                amenity -> !requestedCodes.contains(normalizeCode(amenity.getCode()))
        );
        Set<String> existingCodes = roomType.getAmenities().stream()
                .map(Amenity::getCode)
                .map(this::normalizeCode)
                .collect(Collectors.toSet());
        amenities.stream()
                .filter(amenity -> !existingCodes.contains(normalizeCode(amenity.getCode())))
                .forEach(roomType.getAmenities()::add);
    }

    private Set<Amenity> resolveAmenities(Set<String> amenityCodes) {
        if (amenityCodes == null) {
            throw new BusinessValidationException("Amenity codes are required");
        }
        if (amenityCodes.isEmpty()) {
            return new HashSet<>();
        }

        Set<String> normalizedCodes = amenityCodes.stream()
                .map(this::normalizeCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Amenity> amenities = amenityRepository.findAllByCodeIn(normalizedCodes);
        Set<String> existingCodes = amenities.stream()
                .map(Amenity::getCode)
                .map(this::normalizeCode)
                .collect(Collectors.toSet());

        Set<String> missingCodes = normalizedCodes.stream()
                .filter(amenityCode -> !existingCodes.contains(amenityCode))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!missingCodes.isEmpty()) {
            throw new ResourceNotFoundException("Amenities", String.join(",", missingCodes));
        }

        return new HashSet<>(amenities);
    }

    private void validateOccupancy(int maxOccupancy, int maxAdults, int maxChildren) {
        if (maxAdults > maxOccupancy) {
            throw new BusinessValidationException("Maximum adults cannot exceed maximum occupancy");
        }
        if (maxChildren > maxOccupancy) {
            throw new BusinessValidationException("Maximum children cannot exceed maximum occupancy");
        }
    }

    private void replaceCancellationPolicyOptions(RoomType roomType, Set<String> policyCodes) {
        Set<String> normalizedCodes = normalizePolicyCodes(policyCodes);
        Map<String, RoomTypeCancellationPolicy> existingOptions = roomType.getCancellationPolicyOptions().stream()
                .filter(option -> option.getCancellationPolicy() != null)
                .collect(Collectors.toMap(
                        option -> normalizeCode(option.getCancellationPolicy().getCode()),
                        option -> option,
                        (first, duplicate) -> first
                ));
        roomType.getCancellationPolicyOptions().removeIf(
                option -> option.getCancellationPolicy() == null
                        || !normalizedCodes.contains(normalizeCode(option.getCancellationPolicy().getCode()))
        );

        int sortOrder = 0;
        for (String policyCode : normalizedCodes) {
            RoomTypeCancellationPolicy existingOption = existingOptions.get(policyCode);
            if (existingOption != null) {
                existingOption.setIsActive(true);
                existingOption.setSortOrder(sortOrder++);
                continue;
            }
            CancellationPolicy policy = cancellationPolicyRepository.findByCodeIgnoreCaseAndIsActiveTrue(policyCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Active cancellation policy", policyCode));
            if (roomType.getCancellationPolicy() == null) {
                roomType.setCancellationPolicy(policy);
            }
            roomType.getCancellationPolicyOptions().add(RoomTypeCancellationPolicy.builder()
                    .roomType(roomType)
                    .cancellationPolicy(policy)
                    .isActive(true)
                    .sortOrder(sortOrder++)
                    .build());
        }
    }

    private Set<String> normalizePolicyCodes(Set<String> policyCodes) {
        if (policyCodes == null) {
            throw new BusinessValidationException("Online cancellation policy codes are required");
        }
        return policyCodes.stream()
                .map(this::normalizeCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void validateBookingOptions(RoomType roomType) {
        if (!Boolean.TRUE.equals(roomType.getIsActive())) {
            return;
        }
        boolean hasOnlineOption = roomType.getCancellationPolicyOptions().stream()
                .anyMatch(option -> Boolean.TRUE.equals(option.getIsActive()));
        if (!hasOnlineOption) {
            throw new BusinessValidationException("Active room types must have at least one booking option");
        }
    }

    private RoomTypeResponse mapRoomTypeResponse(RoomType roomType) {
        List<RoomTypeBedResponse> beds = roomType.getBeds().stream()
                .sorted(Comparator.comparingInt(bed -> bed.getBedType().ordinal()))
                .map(bed -> new RoomTypeBedResponse(bed.getBedType(), bed.getQuantity()))
                .toList();
        List<AmenityResponse> amenities = roomType.getAmenities().stream()
                .sorted(Comparator.comparing(Amenity::getSortOrder).thenComparing(Amenity::getCode))
                .map(amenity -> new AmenityResponse(
                        amenity.getCode(),
                        amenity.getName(),
                        amenity.getIcon(),
                        amenity.getCategory(),
                        amenity.getIsFilterable(),
                        amenity.getSortOrder()
                ))
                .toList();

        return new RoomTypeResponse(
                roomType.getCode(),
                roomType.getId(),
                roomType.getName(),
                roomType.getSlug(),
                roomType.getDescription(),
                roomType.getBedCount(),
                roomType.getMaxOccupancy(),
                roomType.getMaxAdults(),
                roomType.getMaxChildren(),
                roomType.getBasePrice(),
                roomType.getCurrency(),
                roomType.getExtraBedPrice(),
                roomType.getSizeSqm(),
                roomType.getIsActive(),
                roomType.getSortOrder(),
                roomType.getPayAtHotelEnabled(),
                roomType.getPayAtHotelPriceAdjustmentPercent(),
                mapOnlinePolicyOptions(roomType),
                mapBookingOptions(roomType),
                beds,
                amenities,
                roomTypeImageService.getImageResponses(roomType),
                roomType.getCreatedAt(),
                roomType.getUpdatedAt()
        );
    }

    private List<RoomTypeCancellationPolicyOptionResponse> mapOnlinePolicyOptions(RoomType roomType) {
        return roomType.getCancellationPolicyOptions().stream()
                .filter(option -> option.getCancellationPolicy() != null)
                .sorted(Comparator.comparing(RoomTypeCancellationPolicy::getSortOrder)
                        .thenComparing(option -> option.getCancellationPolicy().getCode()))
                .map(option -> new RoomTypeCancellationPolicyOptionResponse(
                        mapCancellationPolicyResponse(option.getCancellationPolicy()),
                        option.getIsActive(),
                        option.getSortOrder()
                ))
                .toList();
    }

    private List<RoomTypeBookingOptionResponse> mapBookingOptions(RoomType roomType) {
        List<RoomTypeBookingOptionResponse> options = new java.util.ArrayList<>();
        roomType.getCancellationPolicyOptions().stream()
                .filter(option -> Boolean.TRUE.equals(option.getIsActive()))
                .filter(option -> option.getCancellationPolicy() != null)
                .filter(option -> Boolean.TRUE.equals(option.getCancellationPolicy().getIsActive()))
                .sorted(Comparator.comparing(RoomTypeCancellationPolicy::getSortOrder)
                        .thenComparing(option -> option.getCancellationPolicy().getCode()))
                .map(option -> new RoomTypeBookingOptionResponse(
                        "ONLINE:" + option.getCancellationPolicy().getCode(),
                        BookingPaymentOption.ONLINE,
                        mapCancellationPolicyResponse(option.getCancellationPolicy()),
                        option.getCancellationPolicy().getPriceAdjustmentPercent()
                ))
                .forEach(options::add);

        return List.copyOf(options);
    }

    private CancellationPolicyResponse mapCancellationPolicyResponse(CancellationPolicy policy) {
        if (policy == null) {
            return null;
        }
        List<CancellationPolicyRuleResponse> rules = policy.getRules().stream()
                .sorted(Comparator.comparing(CancellationPolicyRule::getMinHoursBefore).reversed())
                .map(rule -> new CancellationPolicyRuleResponse(
                        rule.getMinHoursBefore(),
                        rule.getRefundPercent()
                ))
                .toList();
        return new CancellationPolicyResponse(
                policy.getCode(),
                policy.getName(),
                policy.getDescription(),
                policy.getNoShowChargePercent(),
                policy.getPriceAdjustmentPercent(),
                policy.getIsDefault(),
                policy.getIsActive(),
                rules,
                policy.getCreatedAt(),
                policy.getUpdatedAt()
        );
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessValidationException("Code cannot be blank");
        }
        return code.strip().toUpperCase(Locale.ROOT);
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return DEFAULT_CURRENCY;
        }
        return currency.strip().toUpperCase(Locale.ROOT);
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

    private BigDecimal normalizePercent(BigDecimal value, BigDecimal defaultValue) {
        BigDecimal normalized = value == null ? defaultValue : value;
        if (normalized == null || normalized.signum() < 0 || normalized.compareTo(new BigDecimal("100.00")) > 0) {
            throw new BusinessValidationException("Price adjustment percent must be between 0 and 100");
        }
        return normalized;
    }
}
