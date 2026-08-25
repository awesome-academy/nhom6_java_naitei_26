package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.serviceitem.ServiceItemOptionResponse;
import com.example.hotelmanagement.entity.ServiceItem;
import com.example.hotelmanagement.repositories.ServiceItemRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@PreAuthorize(PermissionExpressions.INVOICE_ISSUE)
public class ServiceItemService {

    private final ServiceItemRepository serviceItemRepository;

    public ServiceItemService(ServiceItemRepository serviceItemRepository) {
        this.serviceItemRepository = serviceItemRepository;
    }

    public List<ServiceItemOptionResponse> getActiveServiceItems() {
        return serviceItemRepository.findAllByIsActiveTrueOrderByCategoryAscNameAscCodeAsc()
                .stream()
                .map(this::mapResponse)
                .toList();
    }

    private ServiceItemOptionResponse mapResponse(ServiceItem serviceItem) {
        return new ServiceItemOptionResponse(
                serviceItem.getCode(),
                serviceItem.getName(),
                serviceItem.getCategory(),
                serviceItem.getUnitPrice(),
                serviceItem.getTaxPercent()
        );
    }
}
