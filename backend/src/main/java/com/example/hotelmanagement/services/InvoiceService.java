package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.invoice.InvoiceAdjustmentRequest;
import com.example.hotelmanagement.dto.invoice.InvoiceBuyerUpdateRequest;
import com.example.hotelmanagement.dto.invoice.InvoiceItemResponse;
import com.example.hotelmanagement.dto.invoice.InvoiceResponse;
import com.example.hotelmanagement.dto.invoice.InvoiceVoidRequest;
import com.example.hotelmanagement.dto.invoice.InvoiceVoidResponse;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.BookingRoom;
import com.example.hotelmanagement.entity.BookingRoomNight;
import com.example.hotelmanagement.entity.CustomerProfile;
import com.example.hotelmanagement.entity.FolioCharge;
import com.example.hotelmanagement.entity.Invoice;
import com.example.hotelmanagement.entity.InvoiceItem;
import com.example.hotelmanagement.entity.StaffProfile;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.entity.enums.InvoiceLineType;
import com.example.hotelmanagement.entity.enums.InvoicePaymentStatus;
import com.example.hotelmanagement.entity.enums.InvoiceStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.FolioChargeRepository;
import com.example.hotelmanagement.repositories.InvoiceItemRepository;
import com.example.hotelmanagement.repositories.InvoiceRepository;
import com.example.hotelmanagement.repositories.StaffProfileRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Validated
@Transactional
public class InvoiceService {

    private static final int MONEY_SCALE = 2;
    private static final BigDecimal HUNDRED = new BigDecimal("100.00");
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("99999999.99");
    private static final BigDecimal MAX_MONEY = new BigDecimal("999999999999.99");
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(MONEY_SCALE);
    private static final BigDecimal ONE_QUANTITY = BigDecimal.ONE.setScale(MONEY_SCALE);
    private static final int SORT_ORDER_STEP = 10;
    private static final String ROOM_REFERENCE_TYPE = "BOOKING_ROOM_NIGHT";
    private static final String SERVICE_REFERENCE_TYPE = "FOLIO_CHARGE";
    private static final String INVOICE_NUMBER_PREFIX = "INV";
    private static final int INVOICE_NUMBER_MAX_ATTEMPTS = 5;

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final FolioChargeRepository folioChargeRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public InvoiceService(
            InvoiceRepository invoiceRepository,
            InvoiceItemRepository invoiceItemRepository,
            FolioChargeRepository folioChargeRepository,
            StaffProfileRepository staffProfileRepository,
            Clock clock
    ) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceItemRepository = invoiceItemRepository;
        this.folioChargeRepository = folioChargeRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.clock = clock;
    }

    public InvoiceResponse createDraftForCheckout(Booking booking) {
        validateCheckoutBooking(booking);
        if (invoiceRepository.existsByBooking_Id(booking.getId())) {
            throw new DuplicateResourceException(
                    "Invoice",
                    "booking id",
                    booking.getId().toString()
            );
        }

        Invoice invoice = Invoice.builder()
                .publicId(UUID.randomUUID().toString())
                .booking(booking)
                .status(InvoiceStatus.DRAFT)
                .paymentStatus(InvoicePaymentStatus.UNPAID)
                .buyerName(normalizeRequiredText(booking.getContactName(), "Buyer name", 150))
                .buyerAddress(buildBuyerAddress(booking.getCustomerProfile()))
                .buyerEmail(normalizeOptionalText(booking.getContactEmail(), "Buyer email", 255))
                .subtotal(ZERO_MONEY)
                .discountTotal(ZERO_MONEY)
                .taxTotal(ZERO_MONEY)
                .totalAmount(ZERO_MONEY)
                .paidAmount(ZERO_MONEY)
                .refundedAmount(ZERO_MONEY)
                .currency(normalizeCurrency(booking.getCurrency()))
                .build();

        List<InvoiceItem> roomItems = buildRoomItems(invoice, booking);
        List<FolioCharge> charges = folioChargeRepository
                .findAllByBooking_IdAndIsVoidedFalseOrderByChargedAtAscIdAsc(booking.getId());
        List<InvoiceItem> serviceItems = buildServiceItems(invoice, charges, roomItems.size());
        invoice.getItems().addAll(roomItems);
        invoice.getItems().addAll(serviceItems);
        recalculateTotals(invoice);

        return mapResponse(invoiceRepository.saveAndFlush(invoice));
    }

    @Transactional(readOnly = true)
    @PreAuthorize(PermissionExpressions.INVOICE_ISSUE)
    public InvoiceResponse getInvoice(String invoicePublicId) {
        String normalizedPublicId = normalizePublicId(invoicePublicId, "Invoice public id");
        Invoice invoice = invoiceRepository.findByPublicId(normalizedPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", normalizedPublicId));
        return mapResponse(invoice);
    }

    @Transactional(readOnly = true)
    @PreAuthorize(PermissionExpressions.INVOICE_ISSUE)
    public InvoiceResponse getDraftByBooking(String bookingPublicId) {
        String normalizedPublicId = normalizePublicId(bookingPublicId, "Booking public id");
        Invoice invoice = invoiceRepository
                .findFirstByBooking_PublicIdAndStatusOrderByCreatedAtDesc(
                        normalizedPublicId,
                        InvoiceStatus.DRAFT
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Draft invoice for booking",
                        normalizedPublicId
                ));
        return mapResponse(invoice);
    }

    @PreAuthorize(PermissionExpressions.INVOICE_ISSUE)
    public InvoiceResponse updateBuyer(
            String invoicePublicId,
            @Valid InvoiceBuyerUpdateRequest request
    ) {
        if (request == null) {
            throw new BusinessValidationException("Invoice buyer update request is required");
        }
        Invoice invoice = getDraftForUpdate(invoicePublicId);
        invoice.setBuyerName(normalizeRequiredText(request.buyerName(), "Buyer name", 150));
        invoice.setBuyerAddress(normalizeOptionalText(request.buyerAddress(), "Buyer address", 2000));
        invoice.setBuyerTaxCode(normalizeOptionalText(request.buyerTaxCode(), "Buyer tax code", 20));
        invoice.setBuyerEmail(normalizeOptionalText(request.buyerEmail(), "Buyer email", 255));
        return mapResponse(invoiceRepository.saveAndFlush(invoice));
    }

    @PreAuthorize(PermissionExpressions.INVOICE_ISSUE)
    public InvoiceResponse addAdjustment(
            String invoicePublicId,
            @Valid InvoiceAdjustmentRequest request
    ) {
        if (request == null) {
            throw new BusinessValidationException("Invoice adjustment request is required");
        }
        Invoice invoice = getDraftForUpdate(invoicePublicId);
        String description = normalizeRequiredText(request.description(), "Description", 200);
        BigDecimal amount = normalizeAdjustmentAmount(request.amount());

        InvoiceItem adjustment = InvoiceItem.builder()
                .invoice(invoice)
                .lineType(InvoiceLineType.ADJUSTMENT)
                .description(description)
                .quantity(ONE_QUANTITY)
                .unitPrice(amount)
                .lineSubtotal(amount)
                .discountAmount(ZERO_MONEY)
                .taxPercent(ZERO_MONEY)
                .taxAmount(ZERO_MONEY)
                .lineTotal(amount)
                .sortOrder(nextSortOrder(invoice))
                .build();
        invoice.getItems().add(adjustment);
        recalculateTotals(invoice);

        return mapResponse(invoiceRepository.saveAndFlush(invoice));
    }

    @PreAuthorize(PermissionExpressions.INVOICE_ISSUE)
    public InvoiceResponse issue(String invoicePublicId, Long actorUserId) {
        Invoice invoice = getInvoiceForUpdate(invoicePublicId);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new BusinessValidationException("Only a DRAFT invoice can be issued");
        }
        StaffProfile staff = getActingStaff(actorUserId, "issue");

        invoice.setInvoiceNumber(generateInvoiceNumber());
        invoice.setIssuedAt(OffsetDateTime.now(clock));
        invoice.setIssuedBy(staff.getId());
        invoice.setStatus(InvoiceStatus.ISSUED);

        return mapResponse(invoiceRepository.saveAndFlush(invoice));
    }

    /**
     * BR-013: an ISSUED invoice is never edited. Voiding it and, when requested, cloning its
     * lines into a fresh DRAFT (linked via replacesInvoice) is the only correction path.
     */
    @PreAuthorize(PermissionExpressions.INVOICE_VOID)
    public InvoiceVoidResponse voidInvoice(
            String invoicePublicId,
            Long actorUserId,
            @Valid InvoiceVoidRequest request
    ) {
        if (request == null) {
            throw new BusinessValidationException("Invoice void request is required");
        }
        Invoice invoice = getInvoiceForUpdate(invoicePublicId);
        if (invoice.getStatus() != InvoiceStatus.ISSUED) {
            throw new BusinessValidationException("Only an ISSUED invoice can be voided");
        }
        StaffProfile staff = getActingStaff(actorUserId, "void");
        String reason = normalizeRequiredText(request.reason(), "Void reason", 2000);

        invoice.setVoidedAt(OffsetDateTime.now(clock));
        invoice.setVoidedBy(staff.getId());
        invoice.setVoidReason(reason);
        invoice.setStatus(InvoiceStatus.VOID);
        Invoice voidedInvoice = invoiceRepository.saveAndFlush(invoice);

        Invoice replacement = request.createReplacement()
                ? createReplacementDraft(voidedInvoice)
                : null;

        return new InvoiceVoidResponse(
                mapResponse(voidedInvoice),
                replacement == null ? null : mapResponse(replacement)
        );
    }

    private Invoice createReplacementDraft(Invoice voidedInvoice) {
        Invoice replacement = Invoice.builder()
                .publicId(UUID.randomUUID().toString())
                .booking(voidedInvoice.getBooking())
                .replacesInvoice(voidedInvoice)
                .status(InvoiceStatus.DRAFT)
                .paymentStatus(InvoicePaymentStatus.UNPAID)
                .buyerName(voidedInvoice.getBuyerName())
                .buyerAddress(voidedInvoice.getBuyerAddress())
                .buyerTaxCode(voidedInvoice.getBuyerTaxCode())
                .buyerEmail(voidedInvoice.getBuyerEmail())
                .subtotal(ZERO_MONEY)
                .discountTotal(ZERO_MONEY)
                .taxTotal(ZERO_MONEY)
                .totalAmount(ZERO_MONEY)
                .paidAmount(ZERO_MONEY)
                .refundedAmount(ZERO_MONEY)
                .currency(voidedInvoice.getCurrency())
                .build();

        List<InvoiceItem> clonedItems = voidedInvoice.getItems().stream()
                .map(item -> InvoiceItem.builder()
                        .invoice(replacement)
                        .lineType(item.getLineType())
                        .description(item.getDescription())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .lineSubtotal(item.getLineSubtotal())
                        .discountAmount(item.getDiscountAmount())
                        .taxPercent(item.getTaxPercent())
                        .taxAmount(item.getTaxAmount())
                        .lineTotal(item.getLineTotal())
                        .referenceType(item.getReferenceType())
                        .referenceId(item.getReferenceId())
                        .sortOrder(item.getSortOrder())
                        .build())
                .toList();
        replacement.getItems().addAll(clonedItems);
        recalculateTotals(replacement);

        return invoiceRepository.saveAndFlush(replacement);
    }

    private StaffProfile getActingStaff(Long actorUserId, String action) {
        return staffProfileRepository.findByUser_Id(actorUserId)
                .orElseThrow(() -> new BusinessValidationException(
                        "Only staff can " + action + " an invoice"
                ));
    }

    private String generateInvoiceNumber() {
        int year = LocalDate.now(clock).getYear();
        for (int attempt = 0; attempt < INVOICE_NUMBER_MAX_ATTEMPTS; attempt++) {
            String candidate = INVOICE_NUMBER_PREFIX + "-" + year + "-"
                    + String.format("%06d", secureRandom.nextInt(1_000_000));
            if (!invoiceRepository.existsByInvoiceNumber(candidate)) {
                return candidate;
            }
        }
        throw new BusinessValidationException("Unable to generate a unique invoice number, please retry");
    }

    private Invoice getInvoiceForUpdate(String invoicePublicId) {
        String normalizedPublicId = normalizePublicId(invoicePublicId, "Invoice public id");
        return invoiceRepository.findForUpdateByPublicId(normalizedPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", normalizedPublicId));
    }

    @PreAuthorize(PermissionExpressions.INVOICE_ISSUE)
    public InvoiceResponse removeAdjustment(String invoicePublicId, Long itemId) {
        Invoice invoice = getDraftForUpdate(invoicePublicId);
        Long normalizedItemId = validatePositiveId(itemId, "Invoice item id");
        InvoiceItem item = invoice.getItems().stream()
                .filter(candidate -> normalizedItemId.equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Invoice item",
                        normalizedItemId.toString()
                ));
        if (item.getLineType() != InvoiceLineType.ADJUSTMENT) {
            throw new BusinessValidationException("Only ADJUSTMENT invoice items can be removed");
        }

        invoice.getItems().remove(item);
        invoiceItemRepository.delete(item);
        recalculateTotals(invoice);
        return mapResponse(invoiceRepository.saveAndFlush(invoice));
    }

    private List<InvoiceItem> buildRoomItems(Invoice invoice, Booking booking) {
        BigDecimal taxPercent = normalizeTaxPercent(booking.getRoomTaxPercentSnapshot());
        List<RoomNightSnapshot> nights = new ArrayList<>();
        for (BookingRoom bookingRoom : booking.getBookingRooms()) {
            String roomTypeCode = normalizeRequiredText(
                    bookingRoom.getRoomTypeCodeSnapshot(),
                    "Room type code snapshot",
                    30
            );
            String roomTypeName = normalizeRequiredText(
                    bookingRoom.getRoomTypeNameSnapshot(),
                    "Room type name snapshot",
                    120
            );
            for (BookingRoomNight night : bookingRoom.getBookingRoomNights()) {
                if (night.getStayDate() == null) {
                    throw new BusinessValidationException("Room night stay date is required");
                }
                nights.add(new RoomNightSnapshot(
                        night.getId(),
                        roomTypeCode,
                        roomTypeName,
                        night.getStayDate(),
                        normalizeNonNegativeMoney(night.getPrice(), "Room night price")
                ));
            }
        }
        if (nights.isEmpty()) {
            throw new BusinessValidationException(
                    "Cannot create an invoice without booking room nights"
            );
        }

        Map<RoomLineKey, List<RoomNightSnapshot>> groupedNights = nights.stream()
                .sorted(Comparator.comparing(RoomNightSnapshot::stayDate)
                        .thenComparing(RoomNightSnapshot::roomTypeCode)
                        .thenComparing(RoomNightSnapshot::price))
                .collect(Collectors.groupingBy(
                        night -> new RoomLineKey(
                                night.roomTypeCode(),
                                night.roomTypeName(),
                                night.stayDate(),
                                night.price()
                        ),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<InvoiceItem> items = new ArrayList<>();
        int sortOrder = SORT_ORDER_STEP;
        for (Map.Entry<RoomLineKey, List<RoomNightSnapshot>> entry : groupedNights.entrySet()) {
            RoomLineKey key = entry.getKey();
            List<RoomNightSnapshot> groupedSources = entry.getValue();
            BigDecimal quantity = normalizeQuantity(
                    BigDecimal.valueOf(groupedSources.size()),
                    "Room quantity"
            );
            BigDecimal lineSubtotal = normalizeNonNegativeCalculatedMoney(
                    quantity.multiply(key.price()),
                    "Room line subtotal"
            );
            BigDecimal taxAmount = normalizeNonNegativeCalculatedMoney(
                    lineSubtotal.multiply(taxPercent).divide(HUNDRED),
                    "Room tax amount"
            );
            BigDecimal lineTotal = normalizeNonNegativeCalculatedMoney(
                    lineSubtotal.add(taxAmount),
                    "Room line total"
            );
            Long referenceId = groupedSources.size() == 1
                    ? groupedSources.getFirst().sourceId()
                    : null;

            items.add(InvoiceItem.builder()
                    .invoice(invoice)
                    .lineType(InvoiceLineType.ROOM)
                    .description(buildRoomDescription(key))
                    .quantity(quantity)
                    .unitPrice(key.price())
                    .lineSubtotal(lineSubtotal)
                    .discountAmount(ZERO_MONEY)
                    .taxPercent(taxPercent)
                    .taxAmount(taxAmount)
                    .lineTotal(lineTotal)
                    .referenceType(ROOM_REFERENCE_TYPE)
                    .referenceId(referenceId)
                    .sortOrder(sortOrder)
                    .build());
            sortOrder += SORT_ORDER_STEP;
        }
        return items;
    }

    private List<InvoiceItem> buildServiceItems(
            Invoice invoice,
            List<FolioCharge> charges,
            int roomItemCount
    ) {
        List<InvoiceItem> items = new ArrayList<>();
        int sortOrder = (roomItemCount + 1) * SORT_ORDER_STEP;
        for (FolioCharge charge : charges) {
            BigDecimal quantity = normalizeQuantity(charge.getQuantity(), "Service quantity");
            BigDecimal unitPrice = normalizeNonNegativeMoney(
                    charge.getUnitPrice(),
                    "Service unit price"
            );
            BigDecimal lineSubtotal = normalizeNonNegativeMoney(
                    charge.getLineSubtotal(),
                    "Service line subtotal"
            );
            BigDecimal discountAmount = normalizeNonNegativeMoney(
                    charge.getDiscountAmount(),
                    "Service discount amount"
            );
            BigDecimal taxPercent = normalizeTaxPercent(charge.getTaxPercent());
            BigDecimal taxAmount = normalizeNonNegativeMoney(
                    charge.getTaxAmount(),
                    "Service tax amount"
            );
            BigDecimal lineTotal = normalizeNonNegativeMoney(
                    charge.getLineTotal(),
                    "Service line total"
            );
            validateStoredLineFormula(
                    quantity,
                    unitPrice,
                    lineSubtotal,
                    discountAmount,
                    taxAmount,
                    lineTotal
            );

            items.add(InvoiceItem.builder()
                    .invoice(invoice)
                    .lineType(InvoiceLineType.SERVICE)
                    .description(normalizeRequiredText(
                            charge.getDescription(),
                            "Service description",
                            200
                    ))
                    .quantity(quantity)
                    .unitPrice(unitPrice)
                    .lineSubtotal(lineSubtotal)
                    .discountAmount(discountAmount)
                    .taxPercent(taxPercent)
                    .taxAmount(taxAmount)
                    .lineTotal(lineTotal)
                    .referenceType(SERVICE_REFERENCE_TYPE)
                    .referenceId(charge.getId())
                    .sortOrder(sortOrder)
                    .build());
            sortOrder += SORT_ORDER_STEP;
        }
        return items;
    }

    private void validateCheckoutBooking(Booking booking) {
        if (booking == null || booking.getId() == null || booking.getId() <= 0) {
            throw new BusinessValidationException("A persisted booking is required");
        }
        if (booking.getStatus() != BookingStatus.CHECKED_OUT) {
            throw new BusinessValidationException(
                    "A draft invoice can only be created when the booking is checked out"
            );
        }
    }

    private Invoice getDraftForUpdate(String invoicePublicId) {
        Invoice invoice = getInvoiceForUpdate(invoicePublicId);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new BusinessValidationException(
                    "Invoice adjustments can only be changed while the invoice is DRAFT"
            );
        }
        return invoice;
    }

    private void recalculateTotals(Invoice invoice) {
        BigDecimal subtotal = ZERO_MONEY;
        BigDecimal discountTotal = ZERO_MONEY;
        BigDecimal taxTotal = ZERO_MONEY;
        BigDecimal lineTotalSum = ZERO_MONEY;
        for (InvoiceItem item : invoice.getItems()) {
            subtotal = subtotal.add(requireMoney(item.getLineSubtotal(), "Line subtotal"));
            discountTotal = discountTotal.add(
                    normalizeNonNegativeMoney(item.getDiscountAmount(), "Line discount amount")
            );
            taxTotal = taxTotal.add(
                    normalizeNonNegativeMoney(item.getTaxAmount(), "Line tax amount")
            );
            lineTotalSum = lineTotalSum.add(requireMoney(item.getLineTotal(), "Line total"));
        }

        subtotal = normalizeNonNegativeCalculatedMoney(subtotal, "Invoice subtotal");
        discountTotal = normalizeNonNegativeCalculatedMoney(
                discountTotal,
                "Invoice discount total"
        );
        taxTotal = normalizeNonNegativeCalculatedMoney(taxTotal, "Invoice tax total");
        BigDecimal totalAmount = normalizeNonNegativeCalculatedMoney(
                subtotal.subtract(discountTotal).add(taxTotal),
                "Invoice total amount"
        );
        BigDecimal normalizedLineTotalSum = normalizeNonNegativeCalculatedMoney(
                lineTotalSum,
                "Invoice line total sum"
        );
        if (totalAmount.compareTo(normalizedLineTotalSum) != 0) {
            throw new BusinessValidationException(
                    "Invoice item totals do not match the invoice total formula"
            );
        }

        invoice.setSubtotal(subtotal);
        invoice.setDiscountTotal(discountTotal);
        invoice.setTaxTotal(taxTotal);
        invoice.setTotalAmount(totalAmount);
    }

    private void validateStoredLineFormula(
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal lineSubtotal,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal lineTotal
    ) {
        BigDecimal expectedSubtotal = quantity.multiply(unitPrice)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal expectedTotal = lineSubtotal.subtract(discountAmount).add(taxAmount)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (expectedSubtotal.compareTo(lineSubtotal) != 0
                || expectedTotal.compareTo(lineTotal) != 0) {
            throw new BusinessValidationException(
                    "Stored service charge amounts do not satisfy the invoice line formulas"
            );
        }
    }

    private String buildRoomDescription(RoomLineKey key) {
        return normalizeRequiredText(
                key.roomTypeName() + " (" + key.roomTypeCode() + ") — " + key.stayDate(),
                "Room description",
                200
        );
    }

    private String buildBuyerAddress(CustomerProfile customerProfile) {
        if (customerProfile == null) {
            return null;
        }
        String address = Stream.of(
                        customerProfile.getAddressLine(),
                        customerProfile.getProvince(),
                        customerProfile.getCountry()
                )
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.joining(", "));
        return address.isEmpty() ? null : address;
    }

    private int nextSortOrder(Invoice invoice) {
        int currentMaximum = invoice.getItems().stream()
                .map(InvoiceItem::getSortOrder)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);
        if (currentMaximum > Integer.MAX_VALUE - SORT_ORDER_STEP) {
            throw new BusinessValidationException("Invoice item sort order is out of range");
        }
        return currentMaximum + SORT_ORDER_STEP;
    }

    private BigDecimal normalizeAdjustmentAmount(BigDecimal amount) {
        BigDecimal normalized = requireMoney(amount, "Adjustment amount");
        if (normalized.signum() == 0 || normalized.abs().compareTo(MAX_MONEY) > 0) {
            throw new BusinessValidationException(
                    "Adjustment amount must be non-zero and fit DECIMAL(14,2)"
            );
        }
        return normalized;
    }

    private BigDecimal normalizeQuantity(BigDecimal quantity, String fieldName) {
        BigDecimal normalized = requireMoney(quantity, fieldName);
        if (normalized.signum() <= 0 || normalized.compareTo(MAX_QUANTITY) > 0) {
            throw new BusinessValidationException(
                    fieldName + " must be greater than zero and fit DECIMAL(10,2)"
            );
        }
        return normalized;
    }

    private BigDecimal normalizeTaxPercent(BigDecimal taxPercent) {
        BigDecimal normalized = requireMoney(taxPercent, "Tax percent");
        if (normalized.signum() < 0 || normalized.compareTo(HUNDRED) > 0) {
            throw new BusinessValidationException("Tax percent must be between 0 and 100");
        }
        return normalized;
    }

    private BigDecimal normalizeNonNegativeMoney(BigDecimal value, String fieldName) {
        BigDecimal normalized = requireMoney(value, fieldName);
        if (normalized.signum() < 0 || normalized.compareTo(MAX_MONEY) > 0) {
            throw new BusinessValidationException(fieldName + " must fit DECIMAL(14,2)");
        }
        return normalized;
    }

    private BigDecimal normalizeNonNegativeCalculatedMoney(BigDecimal value, String fieldName) {
        BigDecimal normalized = value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (normalized.signum() < 0 || normalized.compareTo(MAX_MONEY) > 0) {
            throw new BusinessValidationException(fieldName + " must fit DECIMAL(14,2)");
        }
        return normalized;
    }

    private BigDecimal requireMoney(BigDecimal value, String fieldName) {
        if (value == null) {
            throw new BusinessValidationException(fieldName + " is required");
        }
        if (value.stripTrailingZeros().scale() > MONEY_SCALE) {
            throw new BusinessValidationException(
                    fieldName + " must have at most 2 decimal places"
            );
        }
        return value.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    private String normalizeCurrency(String currency) {
        String normalized = normalizeRequiredText(currency, "Currency", 3).toUpperCase(Locale.ROOT);
        if (normalized.length() != 3) {
            throw new BusinessValidationException("Currency must contain exactly 3 characters");
        }
        return normalized;
    }

    private String normalizePublicId(String publicId, String fieldName) {
        return normalizeRequiredText(publicId, fieldName, 36);
    }

    private Long validatePositiveId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new BusinessValidationException(fieldName + " must be a positive number");
        }
        return id;
    }

    private String normalizeRequiredText(String value, String fieldName, int maxLength) {
        String normalized = normalizeOptionalText(value, fieldName, maxLength);
        if (normalized == null) {
            throw new BusinessValidationException(fieldName + " cannot be blank");
        }
        return normalized;
    }

    private String normalizeOptionalText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new BusinessValidationException(
                    fieldName + " must not exceed " + maxLength + " characters"
            );
        }
        return normalized;
    }

    private InvoiceResponse mapResponse(Invoice invoice) {
        List<InvoiceItemResponse> items = invoice.getItems().stream()
                .sorted(Comparator.comparing(
                                InvoiceItem::getSortOrder,
                                Comparator.nullsLast(Integer::compareTo)
                        )
                        .thenComparing(
                                InvoiceItem::getId,
                                Comparator.nullsLast(Long::compareTo)
                        ))
                .map(this::mapItemResponse)
                .toList();
        return new InvoiceResponse(
                invoice.getPublicId(),
                invoice.getInvoiceNumber(),
                invoice.getBooking().getPublicId(),
                invoice.getStatus(),
                invoice.getPaymentStatus(),
                invoice.getIssuedAt(),
                invoice.getIssuedBy(),
                invoice.getBuyerName(),
                invoice.getBuyerAddress(),
                invoice.getBuyerTaxCode(),
                invoice.getBuyerEmail(),
                invoice.getSubtotal(),
                invoice.getDiscountTotal(),
                invoice.getTaxTotal(),
                invoice.getTotalAmount(),
                invoice.getPaidAmount(),
                invoice.getRefundedAmount(),
                invoice.getCurrency(),
                items,
                invoice.getCreatedAt(),
                invoice.getUpdatedAt()
        );
    }

    private InvoiceItemResponse mapItemResponse(InvoiceItem item) {
        return new InvoiceItemResponse(
                item.getId(),
                item.getLineType(),
                item.getDescription(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineSubtotal(),
                item.getDiscountAmount(),
                item.getTaxPercent(),
                item.getTaxAmount(),
                item.getLineTotal(),
                item.getReferenceType(),
                item.getReferenceId(),
                item.getSortOrder()
        );
    }

    private record RoomNightSnapshot(
            Long sourceId,
            String roomTypeCode,
            String roomTypeName,
            LocalDate stayDate,
            BigDecimal price
    ) {
    }

    private record RoomLineKey(
            String roomTypeCode,
            String roomTypeName,
            LocalDate stayDate,
            BigDecimal price
    ) {
    }
}
