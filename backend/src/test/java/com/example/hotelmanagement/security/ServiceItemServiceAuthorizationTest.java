package com.example.hotelmanagement.security;

import com.example.hotelmanagement.repositories.ServiceItemRepository;
import com.example.hotelmanagement.services.ServiceItemService;
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
class ServiceItemServiceAuthorizationTest {

    @Autowired
    private ServiceItemService serviceItemService;

    @MockBean
    private ServiceItemRepository serviceItemRepository;

    @Test
    @WithMockUser(authorities = "booking:check_out")
    void serviceRejectsCallerWithoutInvoicePermission() {
        assertThatThrownBy(serviceItemService::getActiveServiceItems)
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(serviceItemRepository);
    }

    @Test
    @WithMockUser(authorities = "invoice:issue")
    void serviceAllowsCallerWithInvoicePermission() {
        when(serviceItemRepository.findAllByIsActiveTrueOrderByCategoryAscNameAscCodeAsc())
                .thenReturn(List.of());

        assertThat(serviceItemService.getActiveServiceItems()).isEmpty();
    }
}
