package com.example.hotelmanagement.security;

import com.example.hotelmanagement.dto.serviceitem.ServiceItemOptionResponse;
import com.example.hotelmanagement.entity.enums.ServiceCategory;
import com.example.hotelmanagement.services.ServiceItemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ServiceItemAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServiceItemService serviceItemService;

    @Test
    void endpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/service-items"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(serviceItemService);
    }

    @Test
    @WithMockUser(authorities = "booking:check_out")
    void endpointRejectsCallerWithoutInvoicePermission() throws Exception {
        mockMvc.perform(get("/api/service-items"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(serviceItemService);
    }

    @Test
    @WithMockUser(authorities = "invoice:issue")
    void endpointReturnsActiveServiceItemOptions() throws Exception {
        when(serviceItemService.getActiveServiceItems()).thenReturn(List.of(
                new ServiceItemOptionResponse(
                        "MINIBAR_WATER",
                        "Minibar water",
                        ServiceCategory.MINIBAR,
                        new BigDecimal("30000.00"),
                        new BigDecimal("10.00")
                )
        ));

        mockMvc.perform(get("/api/service-items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("MINIBAR_WATER"))
                .andExpect(jsonPath("$[0].unitPrice").value(30000.00));

        verify(serviceItemService).getActiveServiceItems();
    }
}
