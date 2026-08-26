package com.example.hotelmanagement.security;

import com.example.hotelmanagement.entity.User;
import com.example.hotelmanagement.entity.enums.UserStatus;
import com.example.hotelmanagement.services.InvoicePdfService;
import com.example.hotelmanagement.services.InvoiceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import java.util.List;

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
    private static final String BUYER_REQUEST = """
            {
              "buyerName": "Nguyen Van A",
              "buyerAddress": "123 Le Loi, Da Nang",
              "buyerTaxCode": "0401234567",
              "buyerEmail": "buyer@example.com"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InvoiceService invoiceService;

    @MockBean
    private InvoicePdfService invoicePdfService;

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
        when(invoiceService.updateBuyer(any(), any())).thenReturn(null);
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
        mockMvc.perform(put("/api/invoices/{invoicePublicId}/buyer", INVOICE_PUBLIC_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BUYER_REQUEST))
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
        verify(invoiceService).updateBuyer(any(), any());
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

    @Test
    @WithMockUser(authorities = "invoice:issue")
    void invalidBuyerIsRejectedBeforeService() throws Exception {
        mockMvc.perform(put("/api/invoices/{invoicePublicId}/buyer", INVOICE_PUBLIC_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "buyerName": " ",
                                  "buyerEmail": "not-an-email"
                                }
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(invoiceService);
    }

    @Test
    void voidRequiresOnlyInvoiceVoidPermission() throws Exception {
        var authenticationToken = UsernamePasswordAuthenticationToken.authenticated(
                principal(42L),
                null,
                List.of(new SimpleGrantedAuthority("invoice:void"))
        );
        when(invoiceService.voidInvoice(any(), any(), any())).thenReturn(null);

        mockMvc.perform(post("/api/invoices/{invoicePublicId}/void", INVOICE_PUBLIC_ID)
                        .with(authentication(authenticationToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Wrong buyer information",
                                  "createReplacement": false
                                }
                                """))
                .andExpect(status().isOk());

        verify(invoiceService).voidInvoice(INVOICE_PUBLIC_ID, 42L, new com.example.hotelmanagement.dto.invoice.InvoiceVoidRequest(
                "Wrong buyer information",
                false
        ));
    }

    @Test
    @WithMockUser(authorities = "invoice:issue")
    void issuePermissionAloneCannotVoidInvoice() throws Exception {
        mockMvc.perform(post("/api/invoices/{invoicePublicId}/void", INVOICE_PUBLIC_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Wrong buyer information",
                                  "createReplacement": false
                                }
                                """))
                .andExpect(status().isForbidden());
        verifyNoInteractions(invoiceService);
    }

    private UserPrincipal principal(Long id) {
        User user = User.builder()
                .publicId("33333333-3333-3333-3333-333333333333")
                .email("staff@example.com")
                .passwordHash("hash")
                .status(UserStatus.ACTIVE)
                .build();
        user.setId(id);
        return UserPrincipal.from(user);
    }
}
