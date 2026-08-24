package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.hotelsettings.HotelSettingsResponse;
import com.example.hotelmanagement.dto.hotelsettings.HotelSettingsUpdateRequest;
import com.example.hotelmanagement.entity.HotelSettings;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.repositories.HotelSettingsRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Manages the hotel-wide operational settings introduced to stop hard-coding standard
 * check-in/checkout times, timezone, currency, and default percentages (BE-6.5 / C-3 / C-4).
 */
@Service
@Validated
@Transactional
public class HotelSettingsService {

    public static final String CHECK_IN_TIME_KEY = "standard_check_in_time";
    public static final String CHECKOUT_TIME_KEY = "default_checkout_time";
    public static final String TIMEZONE_KEY = "hotel_timezone";
    public static final String CURRENCY_KEY = "default_currency";
    public static final String ROOM_TAX_PERCENT_KEY = "default_room_tax_percent";
    public static final String NO_SHOW_CHARGE_PERCENT_KEY = "default_no_show_charge_percent";

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final BigDecimal MAX_PERCENT = new BigDecimal("100.00");
    private static final int MONEY_SCALE = 2;

    private final HotelSettingsRepository hotelSettingsRepository;

    public HotelSettingsService(HotelSettingsRepository hotelSettingsRepository) {
        this.hotelSettingsRepository = hotelSettingsRepository;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(PermissionExpressions.SETTINGS_MANAGE)
    public HotelSettingsResponse getSettings() {
        return mapResponse();
    }

    @PreAuthorize(PermissionExpressions.SETTINGS_MANAGE)
    public HotelSettingsResponse updateSettings(@Valid HotelSettingsUpdateRequest request) {
        if (request == null) {
            throw new BusinessValidationException("Hotel settings update request is required");
        }
        if (request.standardCheckInTime() != null) {
            upsert(CHECK_IN_TIME_KEY, request.standardCheckInTime().format(TIME_FORMAT), HotelSettings.DataType.STRING);
        }
        if (request.defaultCheckoutTime() != null) {
            upsert(CHECKOUT_TIME_KEY, request.defaultCheckoutTime().format(TIME_FORMAT), HotelSettings.DataType.STRING);
        }
        if (request.hotelTimezone() != null) {
            upsert(TIMEZONE_KEY, normalizeTimezone(request.hotelTimezone()), HotelSettings.DataType.STRING);
        }
        if (request.defaultCurrency() != null) {
            upsert(CURRENCY_KEY, normalizeCurrency(request.defaultCurrency()), HotelSettings.DataType.STRING);
        }
        if (request.defaultRoomTaxPercent() != null) {
            upsert(
                    ROOM_TAX_PERCENT_KEY,
                    normalizePercent(request.defaultRoomTaxPercent()).toPlainString(),
                    HotelSettings.DataType.NUMBER
            );
        }
        if (request.defaultNoShowChargePercent() != null) {
            upsert(
                    NO_SHOW_CHARGE_PERCENT_KEY,
                    normalizePercent(request.defaultNoShowChargePercent()).toPlainString(),
                    HotelSettings.DataType.NUMBER
            );
        }
        return mapResponse();
    }

    private void upsert(String key, String value, HotelSettings.DataType dataType) {
        HotelSettings setting = hotelSettingsRepository.findBySettingKey(key)
                .orElseGet(() -> HotelSettings.builder().settingKey(key).build());
        setting.setSettingValue(value);
        setting.setDataType(dataType);
        hotelSettingsRepository.save(setting);
    }

    private HotelSettingsResponse mapResponse() {
        return new HotelSettingsResponse(
                getRequiredTime(CHECK_IN_TIME_KEY),
                getRequiredTime(CHECKOUT_TIME_KEY),
                getRequiredString(TIMEZONE_KEY),
                getRequiredString(CURRENCY_KEY),
                getRequiredPercent(ROOM_TAX_PERCENT_KEY),
                getRequiredPercent(NO_SHOW_CHARGE_PERCENT_KEY)
        );
    }

    private LocalTime getRequiredTime(String key) {
        String value = getRequiredString(key);
        try {
            return LocalTime.parse(value, TIME_FORMAT);
        } catch (DateTimeParseException exception) {
            throw new BusinessValidationException(
                    "Hotel setting '" + key + "' does not contain a valid HH:mm time"
            );
        }
    }

    private BigDecimal getRequiredPercent(String key) {
        BigDecimal value = hotelSettingsRepository.getDecimalValue(key);
        if (value == null) {
            throw new BusinessValidationException("Hotel setting '" + key + "' is not configured");
        }
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private String getRequiredString(String key) {
        String value = hotelSettingsRepository.getStringValue(key);
        if (value == null || value.isBlank()) {
            throw new BusinessValidationException("Hotel setting '" + key + "' is not configured");
        }
        return value;
    }

    private String normalizeTimezone(String value) {
        String normalized = value.strip();
        try {
            ZoneId.of(normalized);
        } catch (DateTimeException exception) {
            throw new BusinessValidationException("Hotel timezone must be a valid IANA zone id");
        }
        return normalized;
    }

    private String normalizeCurrency(String value) {
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (normalized.length() != 3) {
            throw new BusinessValidationException("Default currency must contain exactly 3 characters");
        }
        return normalized;
    }

    private BigDecimal normalizePercent(BigDecimal value) {
        if (value.signum() < 0 || value.compareTo(MAX_PERCENT) > 0) {
            throw new BusinessValidationException("Percentage values must be between 0 and 100");
        }
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
