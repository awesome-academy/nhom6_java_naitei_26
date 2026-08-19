package com.example.hotelmanagement.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Hotel Settings - Cấu hình khách sạn theo BE-6.5 trong PROJECT_PLAN.md
 * Lưu ý: DATABASE_DESIGN không có bảng riêng, nhưng BE-6.5 cần bảng này
 * để tránh hard-code các giá trị như standard_check_in_time, default_checkout_time, v.v.
 */
@Entity
@Table(name = "hotel_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelSettings extends BaseEntity {

    public enum DataType {
        STRING, NUMBER, BOOLEAN, JSON
    }

    @Column(name = "setting_key", nullable = false, unique = true, length = 100)
    private String settingKey;

    @Column(name = "setting_value", columnDefinition = "TEXT")
    private String settingValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false)
    @Builder.Default
    private DataType dataType = DataType.STRING;

    @Column(length = 255)
    private String description;

    /**
     * Lấy giá trị dạng String
     */
    public String getStringValue() {
        return settingValue;
    }

    /**
     * Lấy giá trị dạng Integer
     */
    public Integer getIntegerValue() {
        if (settingValue == null) return null;
        try {
            return Integer.parseInt(settingValue);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Lấy giá trị dạng BigDecimal
     */
    public java.math.BigDecimal getDecimalValue() {
        if (settingValue == null) return null;
        try {
            return new java.math.BigDecimal(settingValue);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Lấy giá trị dạng Boolean
     */
    public Boolean getBooleanValue() {
        if (settingValue == null) return null;
        return "true".equalsIgnoreCase(settingValue) || "1".equals(settingValue);
    }
}
