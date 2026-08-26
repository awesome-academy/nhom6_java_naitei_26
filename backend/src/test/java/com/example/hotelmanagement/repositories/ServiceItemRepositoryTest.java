package com.example.hotelmanagement.repositories;

import com.example.hotelmanagement.entity.ServiceItem;
import com.example.hotelmanagement.entity.enums.ServiceCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ServiceItemRepositoryTest {

    @Autowired
    private ServiceItemRepository serviceItemRepository;

    @Test
    void activeOptionsExcludeInactiveItemsAndUsePublicCatalogOrder() {
        serviceItemRepository.saveAllAndFlush(List.of(
                createServiceItem("SPA_Z", "Zulu", ServiceCategory.SPA, true),
                createServiceItem("MINIBAR_B", "Water", ServiceCategory.MINIBAR, true),
                createServiceItem("MINIBAR_A", "Water", ServiceCategory.MINIBAR, true),
                createServiceItem("FNB_A", "Breakfast", ServiceCategory.FNB, false)
        ));

        List<ServiceItem> options = serviceItemRepository
                .findAllByIsActiveTrueOrderByCategoryAscNameAscCodeAsc();

        assertThat(options)
                .extracting(ServiceItem::getCode)
                .containsExactly("MINIBAR_A", "MINIBAR_B", "SPA_Z");
    }

    private ServiceItem createServiceItem(
            String code,
            String name,
            ServiceCategory category,
            boolean active
    ) {
        return ServiceItem.builder()
                .code(code)
                .name(name)
                .category(category)
                .unitPrice(new BigDecimal("100000.00"))
                .taxPercent(new BigDecimal("10.00"))
                .isActive(active)
                .build();
    }
}
