package com.example.hotelmanagement.security;

import com.example.hotelmanagement.repositories.BookingRepository;
import com.example.hotelmanagement.repositories.FolioChargeRepository;
import com.example.hotelmanagement.repositories.ServiceItemRepository;
import com.example.hotelmanagement.repositories.StaffProfileRepository;
import com.example.hotelmanagement.services.FolioChargeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class FolioChargeServiceAuthorizationTest {

    private static final String BOOKING_PUBLIC_ID = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private FolioChargeService folioChargeService;

    @MockBean
    private FolioChargeRepository folioChargeRepository;

    @MockBean
    private ServiceItemRepository serviceItemRepository;

    @MockBean
    private BookingRepository bookingRepository;

    @MockBean
    private StaffProfileRepository staffProfileRepository;

    @Test
    @WithMockUser(authorities = "booking:check_out")
    void serviceRejectsCallerWithoutInvoicePermission() {
        assertThatThrownBy(() -> folioChargeService.getFolioCharges(BOOKING_PUBLIC_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(
                folioChargeRepository,
                serviceItemRepository,
                bookingRepository,
                staffProfileRepository
        );
    }

    @Test
    @WithMockUser(authorities = "invoice:issue")
    void serviceAllowsCallerWithInvoicePermission() {
        when(bookingRepository.existsByPublicId(BOOKING_PUBLIC_ID)).thenReturn(true);
        when(folioChargeRepository.findAllByBooking_PublicIdOrderByChargedAtAscIdAsc(
                BOOKING_PUBLIC_ID
        )).thenReturn(List.of());

        assertThat(folioChargeService.getFolioCharges(BOOKING_PUBLIC_ID)).isEmpty();
    }
}
