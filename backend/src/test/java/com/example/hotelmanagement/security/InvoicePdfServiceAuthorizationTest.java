package com.example.hotelmanagement.security;

import com.example.hotelmanagement.entity.enums.InvoiceStatus;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.HotelSettingsRepository;
import com.example.hotelmanagement.repositories.InvoiceRepository;
import com.example.hotelmanagement.services.InvoicePdfService;
import com.example.hotelmanagement.services.InvoicePdfStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class InvoicePdfServiceAuthorizationTest {

    private static final String BOOKING_PUBLIC_ID = "11111111-1111-1111-1111-111111111111";
    private static final String INVOICE_PUBLIC_ID = "22222222-2222-2222-2222-222222222222";

    @Autowired
    private InvoicePdfService invoicePdfService;

    @MockBean
    private InvoiceRepository invoiceRepository;

    @MockBean
    private InvoicePdfStorage invoicePdfStorage;

    @MockBean
    private HotelSettingsRepository hotelSettingsRepository;

    @Test
    @WithMockUser(authorities = "invoice:issue")
    void invoiceIssuePermissionCannotUseCustomerPdfLookup() {
        assertThatThrownBy(() -> invoicePdfService.getCustomerDownloadUrl(
                BOOKING_PUBLIC_ID,
                INVOICE_PUBLIC_ID,
                88L
        )).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(invoiceRepository, invoicePdfStorage, hotelSettingsRepository);
    }

    @Test
    @WithMockUser(authorities = "booking:read_own")
    void bookingReadOwnPermissionReachesOwnedPdfQuery() {
        Set<InvoiceStatus> statuses = Set.of(InvoiceStatus.ISSUED, InvoiceStatus.VOID);
        when(invoiceRepository.findCustomerVisibleInvoice(
                INVOICE_PUBLIC_ID,
                BOOKING_PUBLIC_ID,
                88L,
                statuses
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoicePdfService.getCustomerDownloadUrl(
                BOOKING_PUBLIC_ID,
                INVOICE_PUBLIC_ID,
                88L
        )).isInstanceOf(ResourceNotFoundException.class);
        verify(invoiceRepository).findCustomerVisibleInvoice(
                INVOICE_PUBLIC_ID,
                BOOKING_PUBLIC_ID,
                88L,
                statuses
        );
    }
}
