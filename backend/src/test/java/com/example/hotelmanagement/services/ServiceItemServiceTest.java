package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.serviceitem.ServiceItemOptionResponse;
import com.example.hotelmanagement.entity.ServiceItem;
import com.example.hotelmanagement.entity.enums.ServiceCategory;
import com.example.hotelmanagement.repositories.ServiceItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceItemServiceTest {

    @Mock
    private ServiceItemRepository serviceItemRepository;

    @Test
    void getActiveServiceItemsMapsPublicChargeOptions() {
        ServiceItem serviceItem = ServiceItem.builder()
                .code("MINIBAR_WATER")
                .name("Minibar water")
                .category(ServiceCategory.MINIBAR)
                .unitPrice(new BigDecimal("30000.00"))
                .taxPercent(new BigDecimal("10.00"))
                .isActive(true)
                .build();
        when(serviceItemRepository.findAllByIsActiveTrueOrderByCategoryAscNameAscCodeAsc())
                .thenReturn(List.of(serviceItem));
        ServiceItemService service = new ServiceItemService(serviceItemRepository);

        List<ServiceItemOptionResponse> responses = service.getActiveServiceItems();

        assertThat(responses).containsExactly(new ServiceItemOptionResponse(
                "MINIBAR_WATER",
                "Minibar water",
                ServiceCategory.MINIBAR,
                new BigDecimal("30000.00"),
                new BigDecimal("10.00")
        ));
    }
}
