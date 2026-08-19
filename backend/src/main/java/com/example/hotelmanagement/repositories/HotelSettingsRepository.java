package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.HotelSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HotelSettingsRepository extends JpaRepository<HotelSettings, Long> {

    Optional<HotelSettings> findBySettingKey(String settingKey);

    default String getStringValue(String key) {
        return findBySettingKey(key)
                .map(HotelSettings::getStringValue)
                .orElse(null);
    }

    default Integer getIntegerValue(String key) {
        return findBySettingKey(key)
                .map(HotelSettings::getIntegerValue)
                .orElse(null);
    }

    default java.math.BigDecimal getDecimalValue(String key) {
        return findBySettingKey(key)
                .map(HotelSettings::getDecimalValue)
                .orElse(null);
    }

    default Boolean getBooleanValue(String key) {
        return findBySettingKey(key)
                .map(HotelSettings::getBooleanValue)
                .orElse(null);
    }
}
