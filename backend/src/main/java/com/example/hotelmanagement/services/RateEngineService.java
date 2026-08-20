package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.pricing.DailyRateResponse;
import com.example.hotelmanagement.entity.RateOverride;
import com.example.hotelmanagement.entity.Room;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.PricingConfigurationException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.RateOverrideRepository;
import com.example.hotelmanagement.repositories.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class RateEngineService {

    private final RoomRepository roomRepository;
    private final RateOverrideRepository rateOverrideRepository;
    private final RateOverrideWeekdayCodec weekdayCodec;

    public RateEngineService(
            RoomRepository roomRepository,
            RateOverrideRepository rateOverrideRepository,
            RateOverrideWeekdayCodec weekdayCodec
    ) {
        this.roomRepository = roomRepository;
        this.rateOverrideRepository = rateOverrideRepository;
        this.weekdayCodec = weekdayCodec;
    }

    public List<DailyRateResponse> calculateDailyRates(
            Long roomId,
            LocalDate checkInDate,
            LocalDate checkOutDate
    ) {
        validateStayDates(roomId, checkInDate, checkOutDate);

        Room room = roomRepository.findByIdAndDeletedAtIsNull(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room", roomId.toString()));
        Long roomTypeId = getRoomTypeId(room);
        List<ResolvedRateOverride> rateOverrides = rateOverrideRepository
                .findActiveOverridesForPricing(roomId, roomTypeId, checkInDate, checkOutDate)
                .stream()
                .filter(rateOverride -> Boolean.TRUE.equals(rateOverride.getIsActive()))
                .map(this::resolveRateOverride)
                .toList();

        List<DailyRateResponse> dailyRates = new ArrayList<>();
        for (LocalDate date = checkInDate; date.isBefore(checkOutDate); date = date.plusDays(1)) {
            BigDecimal price = selectRateOverride(rateOverrides, date)
                    .map(RateOverride::getPrice)
                    .orElseGet(() -> getFallbackPrice(room));
            dailyRates.add(new DailyRateResponse(date, price));
        }
        return List.copyOf(dailyRates);
    }

    private void validateStayDates(Long roomId, LocalDate checkInDate, LocalDate checkOutDate) {
        if (roomId == null || roomId <= 0) {
            throw new BusinessValidationException("Room id must be a positive number");
        }
        if (checkInDate == null || checkOutDate == null) {
            throw new BusinessValidationException("Check-in date and check-out date are required");
        }
        if (!checkOutDate.isAfter(checkInDate)) {
            throw new BusinessValidationException("Check-out date must be after check-in date");
        }
    }

    private Long getRoomTypeId(Room room) {
        if (room.getRoomType() == null || room.getRoomType().getId() == null) {
            throw new PricingConfigurationException(
                    "Room " + room.getId() + " does not have a valid room type"
            );
        }
        return room.getRoomType().getId();
    }

    private ResolvedRateOverride resolveRateOverride(RateOverride rateOverride) {
        validateRateOverride(rateOverride);
        return new ResolvedRateOverride(
                rateOverride,
                weekdayCodec.decodeWeekdays(rateOverride.getWeekdays(), rateOverride.getId())
        );
    }

    private void validateRateOverride(RateOverride rateOverride) {
        Long rateOverrideId = rateOverride.getId();
        boolean hasRoomTarget = rateOverride.getRoom() != null;
        boolean hasRoomTypeTarget = rateOverride.getRoomType() != null;
        if (hasRoomTarget == hasRoomTypeTarget) {
            throw new PricingConfigurationException(
                    "Rate override " + rateOverrideId + " must target exactly one room or room type"
            );
        }
        if (rateOverride.getStartDate() == null || rateOverride.getEndDate() == null
                || !rateOverride.getEndDate().isAfter(rateOverride.getStartDate())) {
            throw new PricingConfigurationException(
                    "Rate override " + rateOverrideId + " has an invalid date range"
            );
        }
        if (rateOverride.getPrice() == null || rateOverride.getPrice().signum() < 0) {
            throw new PricingConfigurationException(
                    "Rate override " + rateOverrideId + " has an invalid price"
            );
        }
        if (rateOverride.getPriority() == null) {
            throw new PricingConfigurationException(
                    "Rate override " + rateOverrideId + " has no priority"
            );
        }
    }

    private Optional<RateOverride> selectRateOverride(
            List<ResolvedRateOverride> rateOverrides,
            LocalDate date
    ) {
        List<RateOverride> matchingOverrides = rateOverrides.stream()
                .filter(rateOverride -> rateOverride.appliesOn(date))
                .map(ResolvedRateOverride::rateOverride)
                .toList();
        if (matchingOverrides.isEmpty()) {
            return Optional.empty();
        }

        int highestPriority = matchingOverrides.stream()
                .mapToInt(RateOverride::getPriority)
                .max()
                .orElseThrow();
        List<RateOverride> highestPriorityOverrides = matchingOverrides.stream()
                .filter(rateOverride -> rateOverride.getPriority() == highestPriority)
                .toList();
        boolean hasRoomSpecificOverride = highestPriorityOverrides.stream()
                .anyMatch(rateOverride -> rateOverride.getRoom() != null);
        List<RateOverride> mostSpecificOverrides = highestPriorityOverrides.stream()
                .filter(rateOverride -> !hasRoomSpecificOverride || rateOverride.getRoom() != null)
                .toList();

        if (mostSpecificOverrides.size() > 1) {
            String conflictingIds = mostSpecificOverrides.stream()
                    .map(RateOverride::getId)
                    .map(String::valueOf)
                    .sorted()
                    .collect(java.util.stream.Collectors.joining(", "));
            throw new PricingConfigurationException(
                    "Rate overrides " + conflictingIds
                            + " have the same priority and specificity for " + date
            );
        }
        return Optional.of(mostSpecificOverrides.getFirst());
    }

    private BigDecimal getFallbackPrice(Room room) {
        if (room.getPriceOverride() != null) {
            return room.getPriceOverride();
        }
        if (room.getRoomType().getBasePrice() == null) {
            throw new PricingConfigurationException(
                    "Room type " + room.getRoomType().getId() + " has no base price"
            );
        }
        return room.getRoomType().getBasePrice();
    }

    private record ResolvedRateOverride(
            RateOverride rateOverride,
            Set<Integer> weekdays
    ) {
        private boolean appliesOn(LocalDate date) {
            boolean isInDateRange = !date.isBefore(rateOverride.getStartDate())
                    && date.isBefore(rateOverride.getEndDate());
            return isInDateRange
                    && (weekdays == null || weekdays.contains(date.getDayOfWeek().getValue()));
        }
    }
}
