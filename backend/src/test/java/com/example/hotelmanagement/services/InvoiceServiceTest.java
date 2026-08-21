package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.invoice.InvoiceAdjustmentRequest;
import com.example.hotelmanagement.dto.invoice.InvoiceResponse;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.BookingRoom;
import com.example.hotelmanagement.entity.BookingRoomNight;
import com.example.hotelmanagement.entity.CustomerProfile;
import com.example.hotelmanagement.entity.FolioCharge;
import com.example.hotelmanagement.entity.Invoice;
import com.example.hotelmanagement.entity.InvoiceItem;
import com.example.hotelmanagement.entity.enums.BookingRoomStatus;
import com.example.hotelmanagement.entity.enums.BookingStatus;
import com.example.hotelmanagement.entity.enums.InvoiceLineType;
import com.example.hotelmanagement.entity.enums.InvoiceStatus;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.DuplicateResourceException;
import com.example.hotelmanagement.repositories.FolioChargeRepository;
import com.example.hotelmanagement.repositories.InvoiceItemRepository;
import com.example.hotelmanagement.repositories.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    private static final String INVOICE_PUBLIC_ID = "22222222-2222-2222-2222-222222222222";
    private static final String BOOKING_PUBLIC_ID = "11111111-1111-1111-1111-111111111111";

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private InvoiceItemRepository invoiceItemRepository;
    @Mock
    private FolioChargeRepository folioChargeRepository;

    private InvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        invoiceService = new InvoiceService(
                invoiceRepository,
                invoiceItemRepository,
                folioChargeRepository
        );
    }

    @Test
    void createDraftGroupsRoomNightsCopiesServicesAndCalculatesTotals() {
        Booking booking = checkedOutBookingWithRoomNights();
        FolioCharge charge = activeFolioCharge(booking);
        when(invoiceRepository.existsByBooking_Id(booking.getId())).thenReturn(false);
        when(folioChargeRepository
                .findAllByBooking_IdAndIsVoidedFalseOrderByChargedAtAscIdAsc(booking.getId()))
                .thenReturn(List.of(charge));
        stubInvoiceSave();

        InvoiceResponse response = invoiceService.createDraftForCheckout(booking);

        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).saveAndFlush(captor.capture());
        Invoice savedInvoice = captor.getValue();
        assertThat(savedInvoice.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(savedInvoice.getInvoiceNumber()).isNull();
        assertThat(savedInvoice.getIssuedAt()).isNull();
        assertThat(savedInvoice.getBuyerName()).isEqualTo("Nguyen Van A");
        assertThat(savedInvoice.getBuyerAddress()).isEqualTo("12 Nguyen Trai, Da Nang, VN");
        assertThat(savedInvoice.getBuyerEmail()).isEqualTo("guest@example.com");
        assertThat(response.items()).hasSize(3);

        var firstRoomLine = response.items().get(0);
        assertThat(firstRoomLine.lineType()).isEqualTo(InvoiceLineType.ROOM);
        assertThat(firstRoomLine.description()).contains("Deluxe", "2026-08-22");
        assertThat(firstRoomLine.quantity()).isEqualByComparingTo("2.00");
        assertThat(firstRoomLine.unitPrice()).isEqualByComparingTo("100.00");
        assertThat(firstRoomLine.lineSubtotal()).isEqualByComparingTo("200.00");
        assertThat(firstRoomLine.taxAmount()).isEqualByComparingTo("20.00");
        assertThat(firstRoomLine.referenceType()).isEqualTo("BOOKING_ROOM_NIGHT");
        assertThat(firstRoomLine.referenceId()).isNull();

        var secondRoomLine = response.items().get(1);
        assertThat(secondRoomLine.quantity()).isEqualByComparingTo("1.00");
        assertThat(secondRoomLine.unitPrice()).isEqualByComparingTo("150.00");
        assertThat(secondRoomLine.referenceId()).isEqualTo(102L);

        var serviceLine = response.items().get(2);
        assertThat(serviceLine.lineType()).isEqualTo(InvoiceLineType.SERVICE);
        assertThat(serviceLine.description()).isEqualTo("Laundry");
        assertThat(serviceLine.discountAmount()).isEqualByComparingTo("5.00");
        assertThat(serviceLine.referenceType()).isEqualTo("FOLIO_CHARGE");
        assertThat(serviceLine.referenceId()).isEqualTo(201L);

        assertThat(response.subtotal()).isEqualByComparingTo("400.00");
        assertThat(response.discountTotal()).isEqualByComparingTo("5.00");
        assertThat(response.taxTotal()).isEqualByComparingTo("40.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("435.00");
    }

    @Test
    void createDraftRejectsBookingThatIsNotCheckedOut() {
        Booking booking = checkedOutBookingWithRoomNights();
        booking.setStatus(BookingStatus.CHECKED_IN);

        assertThatThrownBy(() -> invoiceService.createDraftForCheckout(booking))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("checked out");
        verifyNoInteractions(invoiceRepository, folioChargeRepository);
    }

    @Test
    void createDraftIncludesNightsFromBothSegmentsAfterRoomChange() {
        Booking booking = checkedOutBooking();
        BookingRoom previousRoom = bookingRoom(booking);
        previousRoom.setStatus(BookingRoomStatus.MOVED_OUT);
        addNight(previousRoom, 111L, "2026-08-22", "100.00");
        BookingRoom currentRoom = bookingRoom(booking);
        currentRoom.setStatus(BookingRoomStatus.COMPLETED);
        addNight(currentRoom, 112L, "2026-08-23", "150.00");
        booking.getBookingRooms().add(previousRoom);
        booking.getBookingRooms().add(currentRoom);
        when(invoiceRepository.existsByBooking_Id(booking.getId())).thenReturn(false);
        when(folioChargeRepository
                .findAllByBooking_IdAndIsVoidedFalseOrderByChargedAtAscIdAsc(booking.getId()))
                .thenReturn(List.of());
        stubInvoiceSave();

        InvoiceResponse response = invoiceService.createDraftForCheckout(booking);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items()).allMatch(item -> item.lineType() == InvoiceLineType.ROOM);
        assertThat(response.subtotal()).isEqualByComparingTo("250.00");
        assertThat(response.taxTotal()).isEqualByComparingTo("25.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("275.00");
    }

    @Test
    void createDraftRejectsDuplicateInvoiceForBooking() {
        Booking booking = checkedOutBookingWithRoomNights();
        when(invoiceRepository.existsByBooking_Id(booking.getId())).thenReturn(true);

        assertThatThrownBy(() -> invoiceService.createDraftForCheckout(booking))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("booking id");
        verifyNoInteractions(folioChargeRepository);
        verify(invoiceRepository, never()).saveAndFlush(any());
    }

    @Test
    void createDraftRejectsBookingWithoutRoomNightSnapshots() {
        Booking booking = checkedOutBooking();
        when(invoiceRepository.existsByBooking_Id(booking.getId())).thenReturn(false);

        assertThatThrownBy(() -> invoiceService.createDraftForCheckout(booking))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("without booking room nights");
        verifyNoInteractions(folioChargeRepository);
        verify(invoiceRepository, never()).saveAndFlush(any());
    }

    @Test
    void addPositiveAdjustmentRaisesDraftTotal() {
        Invoice invoice = draftInvoiceWithRoomLine();
        when(invoiceRepository.findForUpdateByPublicId(INVOICE_PUBLIC_ID))
                .thenReturn(Optional.of(invoice));
        when(invoiceRepository.saveAndFlush(invoice)).thenReturn(invoice);

        InvoiceResponse response = invoiceService.addAdjustment(
                INVOICE_PUBLIC_ID,
                new InvoiceAdjustmentRequest("  Additional late check-out fee  ", money("15.00"))
        );

        assertThat(response.subtotal()).isEqualByComparingTo("115.00");
        assertThat(response.taxTotal()).isEqualByComparingTo("10.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("125.00");
        assertThat(response.items()).filteredOn(
                        item -> item.lineType() == InvoiceLineType.ADJUSTMENT
                )
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.description()).isEqualTo("Additional late check-out fee");
                    assertThat(item.lineTotal()).isEqualByComparingTo("15.00");
                });
    }

    @Test
    void addNegativeAdjustmentLowersDraftTotal() {
        Invoice invoice = draftInvoiceWithRoomLine();
        when(invoiceRepository.findForUpdateByPublicId(INVOICE_PUBLIC_ID))
                .thenReturn(Optional.of(invoice));
        when(invoiceRepository.saveAndFlush(invoice)).thenReturn(invoice);

        InvoiceResponse response = invoiceService.addAdjustment(
                INVOICE_PUBLIC_ID,
                new InvoiceAdjustmentRequest("Goodwill compensation", money("-20.00"))
        );

        assertThat(response.subtotal()).isEqualByComparingTo("80.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("90.00");
    }

    @Test
    void addAdjustmentRejectsZeroAndAmountThatMakesInvoiceNegative() {
        Invoice invoice = draftInvoiceWithRoomLine();
        when(invoiceRepository.findForUpdateByPublicId(INVOICE_PUBLIC_ID))
                .thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> invoiceService.addAdjustment(
                INVOICE_PUBLIC_ID,
                new InvoiceAdjustmentRequest("No change", money("0.00"))
        )).isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("non-zero");

        assertThatThrownBy(() -> invoiceService.addAdjustment(
                INVOICE_PUBLIC_ID,
                new InvoiceAdjustmentRequest("Too large", money("-200.00"))
        )).isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("Invoice subtotal");
        verify(invoiceRepository, never()).saveAndFlush(any());
    }

    @Test
    void removeAdjustmentDeletesOnlyAdjustmentAndRecalculatesTotals() {
        Invoice invoice = draftInvoiceWithRoomLine();
        InvoiceItem adjustment = adjustmentItem(invoice, 99L, money("-20.00"));
        invoice.getItems().add(adjustment);
        invoice.setSubtotal(money("80.00"));
        invoice.setTaxTotal(money("10.00"));
        invoice.setTotalAmount(money("90.00"));
        when(invoiceRepository.findForUpdateByPublicId(INVOICE_PUBLIC_ID))
                .thenReturn(Optional.of(invoice));
        when(invoiceRepository.saveAndFlush(invoice)).thenReturn(invoice);

        InvoiceResponse response = invoiceService.removeAdjustment(INVOICE_PUBLIC_ID, 99L);

        verify(invoiceItemRepository).delete(adjustment);
        assertThat(response.items()).hasSize(1);
        assertThat(response.subtotal()).isEqualByComparingTo("100.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("110.00");
    }

    @Test
    void removeAdjustmentRejectsSourceRoomLine() {
        Invoice invoice = draftInvoiceWithRoomLine();
        InvoiceItem roomItem = invoice.getItems().iterator().next();
        when(invoiceRepository.findForUpdateByPublicId(INVOICE_PUBLIC_ID))
                .thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> invoiceService.removeAdjustment(
                INVOICE_PUBLIC_ID,
                roomItem.getId()
        )).isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("Only ADJUSTMENT");
        verifyNoInteractions(invoiceItemRepository);
    }

    @Test
    void adjustmentsRejectIssuedInvoice() {
        Invoice invoice = draftInvoiceWithRoomLine();
        invoice.setStatus(InvoiceStatus.ISSUED);
        when(invoiceRepository.findForUpdateByPublicId(INVOICE_PUBLIC_ID))
                .thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> invoiceService.addAdjustment(
                INVOICE_PUBLIC_ID,
                new InvoiceAdjustmentRequest("Late fee", money("10.00"))
        )).isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("DRAFT");
        verify(invoiceRepository, never()).saveAndFlush(any());
    }

    private Booking checkedOutBookingWithRoomNights() {
        Booking booking = checkedOutBooking();
        BookingRoom firstRoom = bookingRoom(booking);
        addNight(firstRoom, 101L, "2026-08-22", "100.00");
        addNight(firstRoom, 102L, "2026-08-23", "150.00");
        BookingRoom secondRoom = bookingRoom(booking);
        addNight(secondRoom, 103L, "2026-08-22", "100.00");
        booking.getBookingRooms().add(firstRoom);
        booking.getBookingRooms().add(secondRoom);
        return booking;
    }

    private Booking checkedOutBooking() {
        CustomerProfile profile = CustomerProfile.builder()
                .addressLine("12 Nguyen Trai")
                .city("Da Nang")
                .country("VN")
                .build();
        Booking booking = Booking.builder()
                .publicId(BOOKING_PUBLIC_ID)
                .bookingCode("BK-2026-000001")
                .status(BookingStatus.CHECKED_OUT)
                .contactName("Nguyen Van A")
                .contactEmail("guest@example.com")
                .customerProfile(profile)
                .roomTaxPercentSnapshot(money("10.00"))
                .currency("VND")
                .build();
        booking.setId(10L);
        return booking;
    }

    private BookingRoom bookingRoom(Booking booking) {
        return BookingRoom.builder()
                .booking(booking)
                .roomTypeCodeSnapshot("DELUXE")
                .roomTypeNameSnapshot("Deluxe")
                .build();
    }

    private void addNight(BookingRoom room, Long id, String date, String price) {
        BookingRoomNight night = BookingRoomNight.builder()
                .bookingRoom(room)
                .stayDate(LocalDate.parse(date))
                .price(money(price))
                .build();
        night.setId(id);
        room.getBookingRoomNights().add(night);
    }

    private FolioCharge activeFolioCharge(Booking booking) {
        FolioCharge charge = FolioCharge.builder()
                .booking(booking)
                .description("Laundry")
                .quantity(money("2.00"))
                .unitPrice(money("25.00"))
                .lineSubtotal(money("50.00"))
                .discountAmount(money("5.00"))
                .taxPercent(money("10.00"))
                .taxAmount(money("5.00"))
                .lineTotal(money("50.00"))
                .chargedAt(OffsetDateTime.parse("2026-08-21T07:00:00Z"))
                .isVoided(false)
                .build();
        charge.setId(201L);
        return charge;
    }

    private Invoice draftInvoiceWithRoomLine() {
        Booking booking = Booking.builder()
                .publicId(BOOKING_PUBLIC_ID)
                .contactName("Guest")
                .build();
        Invoice invoice = Invoice.builder()
                .publicId(INVOICE_PUBLIC_ID)
                .booking(booking)
                .status(InvoiceStatus.DRAFT)
                .buyerName("Guest")
                .subtotal(money("100.00"))
                .discountTotal(money("0.00"))
                .taxTotal(money("10.00"))
                .totalAmount(money("110.00"))
                .build();
        invoice.setId(20L);
        InvoiceItem roomItem = InvoiceItem.builder()
                .invoice(invoice)
                .lineType(InvoiceLineType.ROOM)
                .description("Deluxe — 2026-08-22")
                .quantity(money("1.00"))
                .unitPrice(money("100.00"))
                .lineSubtotal(money("100.00"))
                .discountAmount(money("0.00"))
                .taxPercent(money("10.00"))
                .taxAmount(money("10.00"))
                .lineTotal(money("110.00"))
                .sortOrder(10)
                .build();
        roomItem.setId(50L);
        invoice.getItems().add(roomItem);
        return invoice;
    }

    private InvoiceItem adjustmentItem(Invoice invoice, Long id, BigDecimal amount) {
        InvoiceItem adjustment = InvoiceItem.builder()
                .invoice(invoice)
                .lineType(InvoiceLineType.ADJUSTMENT)
                .description("Goodwill compensation")
                .quantity(money("1.00"))
                .unitPrice(amount)
                .lineSubtotal(amount)
                .discountAmount(money("0.00"))
                .taxPercent(money("0.00"))
                .taxAmount(money("0.00"))
                .lineTotal(amount)
                .sortOrder(20)
                .build();
        adjustment.setId(id);
        return adjustment;
    }

    private void stubInvoiceSave() {
        when(invoiceRepository.saveAndFlush(any(Invoice.class)))
                .thenAnswer(invocation -> {
                    Invoice invoice = invocation.getArgument(0);
                    invoice.setId(20L);
                    invoice.setPublicId(INVOICE_PUBLIC_ID);
                    return invoice;
                });
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
