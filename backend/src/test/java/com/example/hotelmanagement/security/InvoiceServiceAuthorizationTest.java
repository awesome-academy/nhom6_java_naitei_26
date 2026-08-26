package com.example.hotelmanagement.security;

import com.example.hotelmanagement.dto.invoice.InvoiceBuyerUpdateRequest;
import com.example.hotelmanagement.entity.enums.InvoiceStatus;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.FolioChargeRepository;
import com.example.hotelmanagement.repositories.InvoiceItemRepository;
import com.example.hotelmanagement.repositories.InvoiceRepository;
import com.example.hotelmanagement.services.InvoiceService;
import com.example.hotelmanagement.dto.invoice.InvoiceVoidRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class InvoiceServiceAuthorizationTest {

    private static final String INVOICE_PUBLIC_ID = "22222222-2222-2222-2222-222222222222";
    private static final String BOOKING_PUBLIC_ID = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private InvoiceService invoiceService;

    @MockBean
    private InvoiceRepository invoiceRepository;

    @MockBean
    private InvoiceItemRepository invoiceItemRepository;

    @MockBean
    private FolioChargeRepository folioChargeRepository;

    @Test
    @WithMockUser(authorities = "booking:check_out")
    void serviceRejectsCallerWithoutInvoicePermission() {
        assertThatThrownBy(() -> invoiceService.getInvoice(INVOICE_PUBLIC_ID))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(invoiceRepository, invoiceItemRepository, folioChargeRepository);
    }

    @Test
    @WithMockUser(authorities = "invoice:issue")
    void serviceAllowsCallerWithInvoicePermission() {
        when(invoiceRepository.findByPublicId(INVOICE_PUBLIC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.getInvoice(INVOICE_PUBLIC_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(invoiceRepository).findByPublicId(INVOICE_PUBLIC_ID);
    }

    @Test
    @WithMockUser(authorities = "invoice:issue")
    void issuePermissionCannotVoidInvoice() {
        assertThatThrownBy(() -> invoiceService.voidInvoice(
                INVOICE_PUBLIC_ID,
                1L,
                new InvoiceVoidRequest("Wrong invoice", false)
        )).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(invoiceRepository, invoiceItemRepository, folioChargeRepository);
    }

    @Test
    @WithMockUser(authorities = "booking:check_out")
    void serviceRejectsBuyerUpdateWithoutInvoiceIssuePermission() {
        assertThatThrownBy(() -> invoiceService.updateBuyer(
                INVOICE_PUBLIC_ID,
                new InvoiceBuyerUpdateRequest("Buyer", null, null, null)
        )).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(invoiceRepository, invoiceItemRepository, folioChargeRepository);
    }

    @Test
    @WithMockUser(authorities = "invoice:void")
    void voidPermissionReachesInvoiceRepository() {
        when(invoiceRepository.findForUpdateByPublicId(INVOICE_PUBLIC_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.voidInvoice(
                INVOICE_PUBLIC_ID,
                1L,
                new InvoiceVoidRequest("Wrong invoice", false)
        )).isInstanceOf(ResourceNotFoundException.class);
        verify(invoiceRepository).findForUpdateByPublicId(INVOICE_PUBLIC_ID);
    }

    @Test
    @WithMockUser(authorities = "invoice:issue")
    void invoiceIssuePermissionCannotUseCustomerInvoiceLookup() {
        assertThatThrownBy(() -> invoiceService.getCustomerInvoice(BOOKING_PUBLIC_ID, 88L))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(invoiceRepository, invoiceItemRepository, folioChargeRepository);
    }

    @Test
    @WithMockUser(authorities = "booking:read_own")
    void bookingReadOwnPermissionReachesOwnedInvoiceQuery() {
        when(invoiceRepository.findCustomerVisibleInvoices(
                BOOKING_PUBLIC_ID,
                88L,
                Set.of(InvoiceStatus.ISSUED, InvoiceStatus.VOID)
        )).thenReturn(List.of());

        assertThatThrownBy(() -> invoiceService.getCustomerInvoice(BOOKING_PUBLIC_ID, 88L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(invoiceRepository).findCustomerVisibleInvoices(
                BOOKING_PUBLIC_ID,
                88L,
                Set.of(InvoiceStatus.ISSUED, InvoiceStatus.VOID)
        );
    }
}
