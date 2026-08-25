package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.hotelsettings.HotelSettingsResponse;
import com.example.hotelmanagement.dto.hotelsettings.HotelSettingsUpdateRequest;
import com.example.hotelmanagement.entity.HotelSettings;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.repositories.HotelSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotelSettingsServiceTest {

    @Mock
    private HotelSettingsRepository hotelSettingsRepository;

    private HotelSettingsService hotelSettingsService;

    @BeforeEach
    void setUp() {
        hotelSettingsService = new HotelSettingsService(hotelSettingsRepository);
    }

    @Test
    void getSettingsMapsAllSeededKeys() {
        stubSeededDefaults();

        HotelSettingsResponse response = hotelSettingsService.getSettings();

        assertThat(response.standardCheckInTime()).isEqualTo(LocalTime.of(14, 0));
        assertThat(response.defaultCheckoutTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(response.hotelTimezone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(response.defaultCurrency()).isEqualTo("VND");
        assertThat(response.defaultRoomTaxPercent()).isEqualByComparingTo("0.00");
        assertThat(response.defaultDepositPercent()).isEqualByComparingTo("30.00");
        assertThat(response.defaultNoShowChargePercent()).isEqualByComparingTo("100.00");
    }

    @Test
    void getSettingsThrowsWhenAKeyIsMissing() {
        when(hotelSettingsRepository.getStringValue(HotelSettingsService.CHECK_IN_TIME_KEY)).thenReturn(null);

        assertThatThrownBy(() -> hotelSettingsService.getSettings())
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining(HotelSettingsService.CHECK_IN_TIME_KEY);
    }

    @Test
    void updateSettingsOnlyTouchesProvidedFields() {
        stubSeededDefaults();
        when(hotelSettingsRepository.findBySettingKey(HotelSettingsService.ROOM_TAX_PERCENT_KEY))
                .thenReturn(Optional.of(existingSetting(HotelSettingsService.ROOM_TAX_PERCENT_KEY, "0.00")));
        when(hotelSettingsRepository.save(any(HotelSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(hotelSettingsRepository.getDecimalValue(HotelSettingsService.ROOM_TAX_PERCENT_KEY))
                .thenReturn(new BigDecimal("8.00"));

        HotelSettingsUpdateRequest request = new HotelSettingsUpdateRequest(
                null, null, null, null, new BigDecimal("8.00"), null, null
        );
        HotelSettingsResponse response = hotelSettingsService.updateSettings(request);

        ArgumentCaptor<HotelSettings> captor = ArgumentCaptor.forClass(HotelSettings.class);
        verify(hotelSettingsRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getSettingKey()).isEqualTo(HotelSettingsService.ROOM_TAX_PERCENT_KEY);
        assertThat(captor.getValue().getSettingValue()).isEqualTo("8.00");
        assertThat(response.defaultRoomTaxPercent()).isEqualByComparingTo("8.00");
    }

    @Test
    void updateSettingsNormalizesCurrencyToUppercase() {
        when(hotelSettingsRepository.findBySettingKey(HotelSettingsService.CURRENCY_KEY))
                .thenReturn(Optional.of(existingSetting(HotelSettingsService.CURRENCY_KEY, "VND")));
        when(hotelSettingsRepository.save(any(HotelSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubSeededDefaults();
        when(hotelSettingsRepository.getStringValue(HotelSettingsService.CURRENCY_KEY)).thenReturn("USD");

        hotelSettingsService.updateSettings(
                new HotelSettingsUpdateRequest(null, null, null, "usd", null, null, null)
        );

        ArgumentCaptor<HotelSettings> captor = ArgumentCaptor.forClass(HotelSettings.class);
        verify(hotelSettingsRepository).save(captor.capture());
        assertThat(captor.getValue().getSettingValue()).isEqualTo("USD");
    }

    @Test
    void updateSettingsRejectsInvalidTimezone() {
        assertThatThrownBy(() -> hotelSettingsService.updateSettings(
                new HotelSettingsUpdateRequest(null, null, "Not/AZone", null, null, null, null)
        )).isInstanceOf(BusinessValidationException.class);
        verify(hotelSettingsRepository, never()).save(any());
    }

    @Test
    void updateSettingsRejectsPercentOutOfRange() {
        assertThatThrownBy(() -> hotelSettingsService.updateSettings(
                new HotelSettingsUpdateRequest(null, null, null, null, new BigDecimal("150.00"), null, null)
        )).isInstanceOf(BusinessValidationException.class);
        verify(hotelSettingsRepository, never()).save(any());
    }

    @Test
    void updateSettingsCreatesRowWhenKeyMissingYet() {
        when(hotelSettingsRepository.findBySettingKey(HotelSettingsService.TIMEZONE_KEY))
                .thenReturn(Optional.empty());
        when(hotelSettingsRepository.save(any(HotelSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubSeededDefaults();
        when(hotelSettingsRepository.getStringValue(HotelSettingsService.TIMEZONE_KEY)).thenReturn("UTC");

        hotelSettingsService.updateSettings(
                new HotelSettingsUpdateRequest(null, null, "UTC", null, null, null, null)
        );

        ArgumentCaptor<HotelSettings> captor = ArgumentCaptor.forClass(HotelSettings.class);
        verify(hotelSettingsRepository).save(captor.capture());
        assertThat(captor.getValue().getSettingKey()).isEqualTo(HotelSettingsService.TIMEZONE_KEY);
        assertThat(captor.getValue().getSettingValue()).isEqualTo("UTC");
    }

    private void stubSeededDefaults() {
        when(hotelSettingsRepository.getStringValue(HotelSettingsService.CHECK_IN_TIME_KEY)).thenReturn("14:00");
        when(hotelSettingsRepository.getStringValue(HotelSettingsService.CHECKOUT_TIME_KEY)).thenReturn("12:00");
        when(hotelSettingsRepository.getStringValue(HotelSettingsService.TIMEZONE_KEY)).thenReturn("Asia/Ho_Chi_Minh");
        when(hotelSettingsRepository.getStringValue(HotelSettingsService.CURRENCY_KEY)).thenReturn("VND");
        when(hotelSettingsRepository.getDecimalValue(HotelSettingsService.ROOM_TAX_PERCENT_KEY))
                .thenReturn(new BigDecimal("0.00"));
        when(hotelSettingsRepository.getDecimalValue(HotelSettingsService.DEPOSIT_PERCENT_KEY))
                .thenReturn(new BigDecimal("30.00"));
        when(hotelSettingsRepository.getDecimalValue(HotelSettingsService.NO_SHOW_CHARGE_PERCENT_KEY))
                .thenReturn(new BigDecimal("100.00"));
    }

    private HotelSettings existingSetting(String key, String value) {
        return HotelSettings.builder()
                .settingKey(key)
                .settingValue(value)
                .dataType(HotelSettings.DataType.STRING)
                .build();
    }
}
