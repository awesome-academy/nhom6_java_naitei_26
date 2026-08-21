package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.foliocharge.FolioChargeCreateRequest;
import com.example.hotelmanagement.dto.foliocharge.FolioChargeResponse;
import com.example.hotelmanagement.dto.foliocharge.FolioChargeVoidRequest;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.FolioCharge;
import com.example.hotelmanagement.entity.ServiceItem;
import com.example.hotelmanagement.entity.StaffProfile;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.entity.enums.ServiceCategory;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.BookingRepository;
import com.example.hotelmanagement.repositories.FolioChargeRepository;
import com.example.hotelmanagement.repositories.ServiceItemRepository;
import com.example.hotelmanagement.repositories.StaffProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FolioChargeServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-21T08:00:00Z"),
            ZoneOffset.UTC
    );
    private static final String BOOKING_PUBLIC_ID = "11111111-1111-1111-1111-111111111111";
    private static final Long STAFF_USER_ID = 42L;

    @Mock
    private FolioChargeRepository folioChargeRepository;
    @Mock
    private ServiceItemRepository serviceItemRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private StaffProfileRepository staffProfileRepository;

    private FolioChargeService folioChargeService;

    @BeforeEach
    void setUp() {
        folioChargeService = new FolioChargeService(
                folioChargeRepository,
                serviceItemRepository,
                bookingRepository,
                staffProfileRepository,
                FIXED_CLOCK
        );
    }

    @Test
    void createServiceItemChargeSnapshotsCurrentValuesAndCalculatesTotals() {
        Booking booking = checkedInBooking();
        StaffProfile staff = staffProfile();
        ServiceItem serviceItem = serviceItem();
        stubBookingAndStaff(booking, staff);
        when(serviceItemRepository.findByCodeIgnoreCaseAndIsActiveTrue("MINIBAR_WATER"))
                .thenReturn(Optional.of(serviceItem));
        when(folioChargeRepository.saveAndFlush(any(FolioCharge.class)))
                .thenAnswer(invocation -> {
                    FolioCharge charge = invocation.getArgument(0);
                    charge.setId(100L);
                    return charge;
                });

        FolioChargeResponse response = folioChargeService.createFolioCharge(
                BOOKING_PUBLIC_ID,
                new FolioChargeCreateRequest(
                        " minibar_water ",
                        null,
                        money("2.00"),
                        null,
                        null
                ),
                STAFF_USER_ID
        );

        ArgumentCaptor<FolioCharge> captor = ArgumentCaptor.forClass(FolioCharge.class);
        verify(folioChargeRepository).saveAndFlush(captor.capture());
        FolioCharge savedCharge = captor.getValue();
        assertThat(savedCharge.getDescription()).isEqualTo("Minibar water");
        assertThat(savedCharge.getUnitPrice()).isEqualByComparingTo("30000.00");
        assertThat(savedCharge.getLineSubtotal()).isEqualByComparingTo("60000.00");
        assertThat(savedCharge.getTaxPercent()).isEqualByComparingTo("10.00");
        assertThat(savedCharge.getTaxAmount()).isEqualByComparingTo("6000.00");
        assertThat(savedCharge.getLineTotal()).isEqualByComparingTo("66000.00");
        assertThat(savedCharge.getDiscountAmount()).isEqualByComparingTo("0.00");
        assertThat(savedCharge.getChargedAt()).isEqualTo(OffsetDateTime.now(FIXED_CLOCK));
        assertThat(savedCharge.getChargedBy()).isEqualTo(staff.getId());
        assertThat(savedCharge.getIsVoided()).isFalse();
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.serviceItemCode()).isEqualTo("MINIBAR_WATER");
        assertThat(response.lineTotal()).isEqualByComparingTo("66000.00");
    }

    @Test
    void createManualChargeCalculatesSnapshotWithDefaultTax() {
        Booking booking = checkedInBooking();
        stubBookingAndStaff(booking, staffProfile());
        when(folioChargeRepository.saveAndFlush(any(FolioCharge.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FolioChargeResponse response = folioChargeService.createFolioCharge(
                BOOKING_PUBLIC_ID,
                new FolioChargeCreateRequest(
                        null,
                        "  Broken glass penalty  ",
                        money("1.50"),
                        money("200000.00"),
                        null
                ),
                STAFF_USER_ID
        );

        assertThat(response.serviceItemCode()).isNull();
        assertThat(response.description()).isEqualTo("Broken glass penalty");
        assertThat(response.lineSubtotal()).isEqualByComparingTo("300000.00");
        assertThat(response.taxPercent()).isEqualByComparingTo("0.00");
        assertThat(response.taxAmount()).isEqualByComparingTo("0.00");
        assertThat(response.lineTotal()).isEqualByComparingTo("300000.00");
        verifyNoInteractions(serviceItemRepository);
    }

    @Test
    void createChargeRejectsClientPriceForServiceItem() {
        stubBookingAndStaff(checkedInBooking(), staffProfile());

        FolioChargeCreateRequest request = new FolioChargeCreateRequest(
                "MINIBAR_WATER",
                null,
                BigDecimal.ONE,
                money("1.00"),
                null
        );

        assertThatThrownBy(() -> folioChargeService.createFolioCharge(
                BOOKING_PUBLIC_ID,
                request,
                STAFF_USER_ID
        )).isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("must be omitted");
        verifyNoInteractions(serviceItemRepository, folioChargeRepository);
    }

    @Test
    void createChargeRejectsBookingThatIsNotCheckedIn() {
        Booking booking = checkedInBooking();
        booking.setStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findForUpdateByPublicId(BOOKING_PUBLIC_ID))
                .thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> folioChargeService.createFolioCharge(
                BOOKING_PUBLIC_ID,
                manualRequest(),
                STAFF_USER_ID
        )).isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("checked in");
        verifyNoInteractions(staffProfileRepository, serviceItemRepository, folioChargeRepository);
    }

    @Test
    void createChargeRejectsInactiveOrMissingServiceItem() {
        stubBookingAndStaff(checkedInBooking(), staffProfile());
        when(serviceItemRepository.findByCodeIgnoreCaseAndIsActiveTrue("MINIBAR_WATER"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> folioChargeService.createFolioCharge(
                BOOKING_PUBLIC_ID,
                new FolioChargeCreateRequest(
                        "MINIBAR_WATER",
                        null,
                        BigDecimal.ONE,
                        null,
                        null
                ),
                STAFF_USER_ID
        )).isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Active service item");
        verify(folioChargeRepository, never()).saveAndFlush(any());
    }

    @Test
    void createChargeRejectsQuantityWithMoreThanTwoDecimalPlaces() {
        stubBookingAndStaff(checkedInBooking(), staffProfile());

        assertThatThrownBy(() -> folioChargeService.createFolioCharge(
                BOOKING_PUBLIC_ID,
                new FolioChargeCreateRequest(
                        null,
                        "Penalty",
                        new BigDecimal("1.001"),
                        money("1000.00"),
                        BigDecimal.ZERO
                ),
                STAFF_USER_ID
        )).isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("at most 2 decimal places");
        verify(folioChargeRepository, never()).saveAndFlush(any());
    }

    @Test
    void createChargeRejectsCalculatedAmountThatExceedsDatabasePrecision() {
        stubBookingAndStaff(checkedInBooking(), staffProfile());

        assertThatThrownBy(() -> folioChargeService.createFolioCharge(
                BOOKING_PUBLIC_ID,
                new FolioChargeCreateRequest(
                        null,
                        "Penalty",
                        money("2.00"),
                        money("999999999999.99"),
                        BigDecimal.ZERO
                ),
                STAFF_USER_ID
        )).isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("Line subtotal")
                .hasMessageContaining("DECIMAL(14,2)");
        verify(folioChargeRepository, never()).saveAndFlush(any());
    }

    @Test
    void voidChargeSetsAuditFieldsWithoutDeleting() {
        Booking booking = checkedInBooking();
        StaffProfile staff = staffProfile();
        FolioCharge charge = existingCharge(booking);
        stubBookingAndStaff(booking, staff);
        when(folioChargeRepository.findForUpdateByIdAndBookingId(100L, booking.getId()))
                .thenReturn(Optional.of(charge));
        when(folioChargeRepository.saveAndFlush(charge)).thenReturn(charge);

        FolioChargeResponse response = folioChargeService.voidFolioCharge(
                BOOKING_PUBLIC_ID,
                100L,
                new FolioChargeVoidRequest("  Entered twice  "),
                STAFF_USER_ID
        );

        assertThat(charge.getIsVoided()).isTrue();
        assertThat(charge.getVoidedAt()).isEqualTo(OffsetDateTime.now(FIXED_CLOCK));
        assertThat(charge.getVoidedBy()).isEqualTo(staff.getId());
        assertThat(charge.getVoidReason()).isEqualTo("Entered twice");
        assertThat(response.isVoided()).isTrue();
        verify(folioChargeRepository).saveAndFlush(charge);
        verify(folioChargeRepository, never()).delete(any());
    }

    @Test
    void voidChargeRejectsAlreadyVoidedCharge() {
        Booking booking = checkedInBooking();
        FolioCharge charge = existingCharge(booking);
        charge.setIsVoided(true);
        stubBookingAndStaff(booking, staffProfile());
        when(folioChargeRepository.findForUpdateByIdAndBookingId(100L, booking.getId()))
                .thenReturn(Optional.of(charge));

        assertThatThrownBy(() -> folioChargeService.voidFolioCharge(
                BOOKING_PUBLIC_ID,
                100L,
                new FolioChargeVoidRequest("Duplicate"),
                STAFF_USER_ID
        )).isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("already voided");
        verify(folioChargeRepository, never()).saveAndFlush(any());
    }

    @Test
    void getFolioChargesRejectsUnknownBooking() {
        when(bookingRepository.existsByPublicId(BOOKING_PUBLIC_ID)).thenReturn(false);

        assertThatThrownBy(() -> folioChargeService.getFolioCharges(BOOKING_PUBLIC_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Booking");
        verifyNoInteractions(folioChargeRepository);
    }

    private void stubBookingAndStaff(Booking booking, StaffProfile staff) {
        when(bookingRepository.findForUpdateByPublicId(BOOKING_PUBLIC_ID))
                .thenReturn(Optional.of(booking));
        when(staffProfileRepository.findByUser_Id(STAFF_USER_ID)).thenReturn(Optional.of(staff));
    }

    private Booking checkedInBooking() {
        Booking booking = Booking.builder()
                .publicId(BOOKING_PUBLIC_ID)
                .bookingCode("BK-2026-000001")
                .status(BookingStatus.CHECKED_IN)
                .contactName("Guest")
                .build();
        booking.setId(10L);
        return booking;
    }

    private StaffProfile staffProfile() {
        StaffProfile staff = StaffProfile.builder()
                .employeeCode("NV001")
                .build();
        staff.setId(20L);
        return staff;
    }

    private ServiceItem serviceItem() {
        ServiceItem serviceItem = ServiceItem.builder()
                .code("MINIBAR_WATER")
                .name("Minibar water")
                .category(ServiceCategory.MINIBAR)
                .unitPrice(money("30000.00"))
                .taxPercent(money("10.00"))
                .isActive(true)
                .build();
        serviceItem.setId(30L);
        return serviceItem;
    }

    private FolioCharge existingCharge(Booking booking) {
        FolioCharge charge = FolioCharge.builder()
                .booking(booking)
                .description("Minibar water")
                .quantity(BigDecimal.ONE.setScale(2))
                .unitPrice(money("30000.00"))
                .lineSubtotal(money("30000.00"))
                .discountAmount(BigDecimal.ZERO.setScale(2))
                .taxPercent(BigDecimal.ZERO.setScale(2))
                .taxAmount(BigDecimal.ZERO.setScale(2))
                .lineTotal(money("30000.00"))
                .chargedAt(OffsetDateTime.now(FIXED_CLOCK).minusHours(1))
                .chargedBy(20L)
                .isVoided(false)
                .build();
        charge.setId(100L);
        return charge;
    }

    private FolioChargeCreateRequest manualRequest() {
        return new FolioChargeCreateRequest(
                null,
                "Penalty",
                BigDecimal.ONE,
                money("100000.00"),
                BigDecimal.ZERO
        );
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
