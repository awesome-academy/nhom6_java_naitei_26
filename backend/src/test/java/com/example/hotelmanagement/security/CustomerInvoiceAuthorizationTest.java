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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerInvoiceAuthorizationTest {

    private static final String BOOKING_PUBLIC_ID = "11111111-1111-1111-1111-111111111111";
    private static final String INVOICE_PUBLIC_ID = "22222222-2222-2222-2222-222222222222";
    private static final Long CUSTOMER_USER_ID = 88L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InvoiceService invoiceService;

    @MockBean
    private InvoicePdfService invoicePdfService;

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/bookings/{bookingPublicId}/invoice", BOOKING_PUBLIC_ID))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(invoiceService, invoicePdfService);
    }

    @Test
    void invoiceIssuePermissionCannotReadCustomerInvoice() throws Exception {
        var authenticationToken = authenticationWith("invoice:issue");

        mockMvc.perform(get("/api/bookings/{bookingPublicId}/invoice", BOOKING_PUBLIC_ID)
                        .with(authentication(authenticationToken)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(invoiceService, invoicePdfService);
    }

    @Test
    void bookingReadOwnPermissionAllowsInvoiceAndPdfLookup() throws Exception {
        var authenticationToken = authenticationWith("booking:read_own");
        when(invoiceService.getCustomerInvoice(BOOKING_PUBLIC_ID, CUSTOMER_USER_ID))
                .thenReturn(null);
        when(invoicePdfService.getCustomerDownloadUrl(
                BOOKING_PUBLIC_ID,
                INVOICE_PUBLIC_ID,
                CUSTOMER_USER_ID
        )).thenReturn(null);

        mockMvc.perform(get("/api/bookings/{bookingPublicId}/invoice", BOOKING_PUBLIC_ID)
                        .with(authentication(authenticationToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get(
                        "/api/bookings/{bookingPublicId}/invoices/{invoicePublicId}/pdf",
                        BOOKING_PUBLIC_ID,
                        INVOICE_PUBLIC_ID
                ).with(authentication(authenticationToken)))
                .andExpect(status().isOk());

        verify(invoiceService).getCustomerInvoice(BOOKING_PUBLIC_ID, CUSTOMER_USER_ID);
        verify(invoicePdfService).getCustomerDownloadUrl(
                BOOKING_PUBLIC_ID,
                INVOICE_PUBLIC_ID,
                CUSTOMER_USER_ID
        );
    }

    private UsernamePasswordAuthenticationToken authenticationWith(String authority) {
        User user = User.builder()
                .publicId("33333333-3333-3333-3333-333333333333")
                .email("customer@example.com")
                .passwordHash("hash")
                .status(UserStatus.ACTIVE)
                .build();
        user.setId(CUSTOMER_USER_ID);
        return UsernamePasswordAuthenticationToken.authenticated(
                UserPrincipal.from(user),
                null,
                List.of(new SimpleGrantedAuthority(authority))
        );
    }
}
