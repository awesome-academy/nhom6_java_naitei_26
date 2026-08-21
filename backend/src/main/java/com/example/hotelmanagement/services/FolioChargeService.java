package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.foliocharge.FolioChargeCreateRequest;
import com.example.hotelmanagement.dto.foliocharge.FolioChargeResponse;
import com.example.hotelmanagement.dto.foliocharge.FolioChargeVoidRequest;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.FolioCharge;
import com.example.hotelmanagement.entity.ServiceItem;
import com.example.hotelmanagement.entity.StaffProfile;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.BookingRepository;
import com.example.hotelmanagement.repositories.FolioChargeRepository;
import com.example.hotelmanagement.repositories.ServiceItemRepository;
import com.example.hotelmanagement.repositories.StaffProfileRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

@Service
@Validated
@Transactional
@PreAuthorize(PermissionExpressions.INVOICE_ISSUE)
public class FolioChargeService {

    private static final int MONEY_SCALE = 2;
    private static final BigDecimal HUNDRED = new BigDecimal("100.00");
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("99999999.99");
    private static final BigDecimal MAX_MONEY = new BigDecimal("999999999999.99");

    private final FolioChargeRepository folioChargeRepository;
    private final ServiceItemRepository serviceItemRepository;
    private final BookingRepository bookingRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final Clock clock;

    public FolioChargeService(
            FolioChargeRepository folioChargeRepository,
            ServiceItemRepository serviceItemRepository,
            BookingRepository bookingRepository,
            StaffProfileRepository staffProfileRepository,
            Clock clock
    ) {
        this.folioChargeRepository = folioChargeRepository;
        this.serviceItemRepository = serviceItemRepository;
        this.bookingRepository = bookingRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<FolioChargeResponse> getFolioCharges(String bookingPublicId) {
        String normalizedBookingPublicId = normalizeBookingPublicId(bookingPublicId);
        if (!bookingRepository.existsByPublicId(normalizedBookingPublicId)) {
            throw new ResourceNotFoundException("Booking", normalizedBookingPublicId);
        }
        return folioChargeRepository
                .findAllByBooking_PublicIdOrderByChargedAtAscIdAsc(normalizedBookingPublicId)
                .stream()
                .map(this::mapResponse)
                .toList();
    }

    public FolioChargeResponse createFolioCharge(
            String bookingPublicId,
            @Valid FolioChargeCreateRequest request,
            Long staffUserId
    ) {
        Booking booking = getChargeableBookingForUpdate(bookingPublicId);
        StaffProfile staff = getStaffProfile(staffUserId);
        ChargeSnapshot snapshot = resolveChargeSnapshot(request);
        BigDecimal quantity = normalizeQuantity(request.quantity());
        BigDecimal lineSubtotal = normalizeCalculatedMoney(
                quantity.multiply(snapshot.unitPrice()),
                "Line subtotal"
        );
        BigDecimal taxAmount = normalizeCalculatedMoney(
                lineSubtotal.multiply(snapshot.taxPercent()).divide(HUNDRED),
                "Tax amount"
        );
        BigDecimal lineTotal = normalizeCalculatedMoney(
                lineSubtotal.add(taxAmount),
                "Line total"
        );

        FolioCharge charge = FolioCharge.builder()
                .booking(booking)
                .serviceItem(snapshot.serviceItem())
                .description(snapshot.description())
                .quantity(quantity)
                .unitPrice(snapshot.unitPrice())
                .lineSubtotal(lineSubtotal)
                .discountAmount(BigDecimal.ZERO.setScale(MONEY_SCALE))
                .taxPercent(snapshot.taxPercent())
                .taxAmount(taxAmount)
                .lineTotal(lineTotal)
                .chargedAt(OffsetDateTime.now(clock))
                .chargedBy(staff.getId())
                .isVoided(false)
                .build();

        return mapResponse(folioChargeRepository.saveAndFlush(charge));
    }

    public FolioChargeResponse voidFolioCharge(
            String bookingPublicId,
            Long chargeId,
            @Valid FolioChargeVoidRequest request,
            Long staffUserId
    ) {
        Booking booking = getChargeableBookingForUpdate(bookingPublicId);
        Long normalizedChargeId = validatePositiveId(chargeId, "Folio charge id");
        StaffProfile staff = getStaffProfile(staffUserId);
        String voidReason = normalizeRequiredText(request.reason(), "Void reason", 2000);
        FolioCharge charge = folioChargeRepository
                .findForUpdateByIdAndBookingId(normalizedChargeId, booking.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Folio charge",
                        normalizedChargeId.toString()
                ));
        if (Boolean.TRUE.equals(charge.getIsVoided())) {
            throw new BusinessValidationException("Folio charge is already voided");
        }

        charge.setIsVoided(true);
        charge.setVoidedAt(OffsetDateTime.now(clock));
        charge.setVoidedBy(staff.getId());
        charge.setVoidReason(voidReason);

        return mapResponse(folioChargeRepository.saveAndFlush(charge));
    }

    private Booking getChargeableBookingForUpdate(String bookingPublicId) {
        String normalizedBookingPublicId = normalizeBookingPublicId(bookingPublicId);
        Booking booking = bookingRepository.findForUpdateByPublicId(normalizedBookingPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", normalizedBookingPublicId));
        if (booking.getStatus() != BookingStatus.CHECKED_IN) {
            throw new BusinessValidationException(
                    "Folio charges can only be changed while the booking is checked in"
            );
        }
        return booking;
    }

    private StaffProfile getStaffProfile(Long staffUserId) {
        Long normalizedStaffUserId = validatePositiveId(staffUserId, "Staff user id");
        return staffProfileRepository.findByUser_Id(normalizedStaffUserId)
                .orElseThrow(() -> new BusinessValidationException(
                        "Only staff can manage folio charges"
                ));
    }

    private ChargeSnapshot resolveChargeSnapshot(FolioChargeCreateRequest request) {
        if (request == null) {
            throw new BusinessValidationException("Folio charge request is required");
        }
        String serviceItemCode = normalizeOptionalText(request.serviceItemCode());
        boolean hasServiceItem = serviceItemCode != null;
        boolean hasManualData = normalizeOptionalText(request.description()) != null
                || request.unitPrice() != null
                || request.taxPercent() != null;

        if (hasServiceItem && hasManualData) {
            throw new BusinessValidationException(
                    "Manual description, unit price and tax percent must be omitted for a service item charge"
            );
        }
        if (hasServiceItem) {
            return resolveServiceItemSnapshot(serviceItemCode);
        }
        return resolveManualSnapshot(request);
    }

    private ChargeSnapshot resolveServiceItemSnapshot(String serviceItemCode) {
        if (serviceItemCode.length() > 40) {
            throw new BusinessValidationException("Service item code must not exceed 40 characters");
        }
        String normalizedCode = serviceItemCode.toUpperCase(Locale.ROOT);
        ServiceItem serviceItem = serviceItemRepository
                .findByCodeIgnoreCaseAndIsActiveTrue(normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("Active service item", normalizedCode));
        String description = normalizeRequiredText(serviceItem.getName(), "Service item name", 200);
        BigDecimal unitPrice = normalizeUnitPrice(serviceItem.getUnitPrice());
        BigDecimal taxPercent = normalizeTaxPercent(serviceItem.getTaxPercent());
        return new ChargeSnapshot(serviceItem, description, unitPrice, taxPercent);
    }

    private ChargeSnapshot resolveManualSnapshot(FolioChargeCreateRequest request) {
        String description = normalizeRequiredText(request.description(), "Description", 200);
        BigDecimal unitPrice = normalizeUnitPrice(request.unitPrice());
        BigDecimal taxPercent = request.taxPercent() == null
                ? BigDecimal.ZERO.setScale(MONEY_SCALE)
                : normalizeTaxPercent(request.taxPercent());
        return new ChargeSnapshot(null, description, unitPrice, taxPercent);
    }

    private BigDecimal normalizeQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0 || quantity.compareTo(MAX_QUANTITY) > 0) {
            throw new BusinessValidationException(
                    "Quantity must be greater than zero and fit DECIMAL(10,2)"
            );
        }
        return setScaleWithoutRounding(quantity, "Quantity");
    }

    private BigDecimal normalizeUnitPrice(BigDecimal unitPrice) {
        if (unitPrice == null || unitPrice.signum() < 0 || unitPrice.compareTo(MAX_MONEY) > 0) {
            throw new BusinessValidationException(
                    "Unit price must be zero or greater and fit DECIMAL(14,2)"
            );
        }
        return setScaleWithoutRounding(unitPrice, "Unit price");
    }

    private BigDecimal normalizeTaxPercent(BigDecimal taxPercent) {
        if (taxPercent == null || taxPercent.signum() < 0 || taxPercent.compareTo(HUNDRED) > 0) {
            throw new BusinessValidationException("Tax percent must be between 0 and 100");
        }
        return setScaleWithoutRounding(taxPercent, "Tax percent");
    }

    private BigDecimal setScaleWithoutRounding(BigDecimal value, String fieldName) {
        try {
            return value.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BusinessValidationException(fieldName + " must have at most 2 decimal places");
        }
    }

    private BigDecimal normalizeCalculatedMoney(BigDecimal value, String fieldName) {
        BigDecimal normalized = value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (normalized.signum() < 0 || normalized.compareTo(MAX_MONEY) > 0) {
            throw new BusinessValidationException(fieldName + " must fit DECIMAL(14,2)");
        }
        return normalized;
    }

    private String normalizeBookingPublicId(String publicId) {
        return normalizeRequiredText(publicId, "Booking public id", 36);
    }

    private Long validatePositiveId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new BusinessValidationException(fieldName + " must be a positive number");
        }
        return id;
    }

    private String normalizeRequiredText(String value, String fieldName, int maxLength) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            throw new BusinessValidationException(fieldName + " cannot be blank");
        }
        if (normalized.length() > maxLength) {
            throw new BusinessValidationException(
                    fieldName + " must not exceed " + maxLength + " characters"
            );
        }
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private FolioChargeResponse mapResponse(FolioCharge charge) {
        return new FolioChargeResponse(
                charge.getId(),
                charge.getBooking().getPublicId(),
                charge.getServiceItem() == null ? null : charge.getServiceItem().getCode(),
                charge.getDescription(),
                charge.getQuantity(),
                charge.getUnitPrice(),
                charge.getLineSubtotal(),
                charge.getDiscountAmount(),
                charge.getTaxPercent(),
                charge.getTaxAmount(),
                charge.getLineTotal(),
                charge.getChargedAt(),
                charge.getChargedBy(),
                charge.getIsVoided(),
                charge.getVoidedAt(),
                charge.getVoidedBy(),
                charge.getVoidReason()
        );
    }

    private record ChargeSnapshot(
            ServiceItem serviceItem,
            String description,
            BigDecimal unitPrice,
            BigDecimal taxPercent
    ) {
    }
}
