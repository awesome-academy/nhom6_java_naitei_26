package com.example.hotelmanagement.security;

import com.example.hotelmanagement.services.InvoiceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvoiceAuthorizationTest {

    private static final String INVOICE_PUBLIC_ID = "22222222-2222-2222-2222-222222222222";
    private static final String BOOKING_PUBLIC_ID = "11111111-1111-1111-1111-111111111111";
    private static final String ADJUSTMENT_REQUEST = """
            {
              "description": "Goodwill compensation",
              "amount": -10000.00
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InvoiceService invoiceService;

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/invoices/{invoicePublicId}", INVOICE_PUBLIC_ID))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(invoiceService);
    }

    @Test
    @WithMockUser(authorities = "booking:check_out")
    void bookingPermissionCannotManageInvoices() throws Exception {
        mockMvc.perform(get("/api/invoices/{invoicePublicId}", INVOICE_PUBLIC_ID))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/invoices/{invoicePublicId}/adjustments", INVOICE_PUBLIC_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ADJUSTMENT_REQUEST))
                .andExpect(status().isForbidden());
        verifyNoInteractions(invoiceService);
    }

    @Test
    @WithMockUser(authorities = "invoice:issue")
    void invoicePermissionAllowsDraftManagement() throws Exception {
        when(invoiceService.getInvoice(INVOICE_PUBLIC_ID)).thenReturn(null);
        when(invoiceService.getDraftByBooking(BOOKING_PUBLIC_ID)).thenReturn(null);
        when(invoiceService.addAdjustment(any(), any())).thenReturn(null);
        when(invoiceService.removeAdjustment(INVOICE_PUBLIC_ID, 99L)).thenReturn(null);

        mockMvc.perform(get("/api/invoices/{invoicePublicId}", INVOICE_PUBLIC_ID))
                .andExpect(status().isOk());
        mockMvc.perform(get(
                        "/api/bookings/{bookingPublicId}/invoices/draft",
                        BOOKING_PUBLIC_ID
                ))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/invoices/{invoicePublicId}/adjustments", INVOICE_PUBLIC_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ADJUSTMENT_REQUEST))
                .andExpect(status().isOk());
        mockMvc.perform(delete(
                        "/api/invoices/{invoicePublicId}/adjustments/{itemId}",
                        INVOICE_PUBLIC_ID,
                        99L
                ))
                .andExpect(status().isOk());

        verify(invoiceService).getInvoice(INVOICE_PUBLIC_ID);
        verify(invoiceService).getDraftByBooking(BOOKING_PUBLIC_ID);
        verify(invoiceService).addAdjustment(any(), any());
        verify(invoiceService).removeAdjustment(INVOICE_PUBLIC_ID, 99L);
    }

    @Test
    @WithMockUser(authorities = "invoice:issue")
    void invalidAdjustmentIsRejectedBeforeService() throws Exception {
        mockMvc.perform(post("/api/invoices/{invoicePublicId}/adjustments", INVOICE_PUBLIC_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": " ",
                                  "amount": 1.001
                                }
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(invoiceService);
    }
}
