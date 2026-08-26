package com.example.hotelmanagement.services;

import com.example.hotelmanagement.dto.email.BookingEmailRequest;
import com.example.hotelmanagement.dto.email.BookingEmailResponse;
import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.EmailMessage;
import com.example.hotelmanagement.exceptions.BusinessValidationException;
import com.example.hotelmanagement.exceptions.ResourceNotFoundException;
import com.example.hotelmanagement.repositories.BookingRepository;
import com.example.hotelmanagement.repositories.EmailMessageRepository;
import com.example.hotelmanagement.security.PermissionExpressions;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class BookingEmailService {

    private static final int HISTORY_LIMIT = 20;

    private final BookingRepository bookingRepository;
    private final EmailMessageRepository emailMessageRepository;
    private final QueuedEmailService queuedEmailService;

    public BookingEmailService(
            BookingRepository bookingRepository,
            EmailMessageRepository emailMessageRepository,
            QueuedEmailService queuedEmailService
    ) {
        this.bookingRepository = bookingRepository;
        this.emailMessageRepository = emailMessageRepository;
        this.queuedEmailService = queuedEmailService;
    }

    @PreAuthorize(PermissionExpressions.EMAIL_SEND)
    public BookingEmailResponse queueBookingEmail(
            String bookingPublicId,
            BookingEmailRequest request,
            Long actorUserId
    ) {
        Booking booking = findBooking(bookingPublicId);
        if (booking.getContactEmail() == null || booking.getContactEmail().isBlank()) {
            throw new BusinessValidationException("Booking contact email is required to send an email");
        }
        return mapResponse(queuedEmailService.queueCustomBookingEmail(
                booking,
                request.subject(),
                request.body(),
                actorUserId
        ));
    }

    @Transactional(readOnly = true)
    @PreAuthorize(PermissionExpressions.EMAIL_SEND)
    public List<BookingEmailResponse> getBookingEmailHistory(String bookingPublicId) {
        Booking booking = findBooking(bookingPublicId);
        return emailMessageRepository.findTop20ByRelatedBookingIdOrderByCreatedAtDesc(booking.getId()).stream()
                .limit(HISTORY_LIMIT)
                .map(this::mapResponse)
                .toList();
    }

    private Booking findBooking(String bookingPublicId) {
        return bookingRepository.findByPublicId(bookingPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingPublicId));
    }

    private BookingEmailResponse mapResponse(EmailMessage message) {
        return new BookingEmailResponse(
                message.getId(),
                message.getToEmail(),
                message.getSubject(),
                message.getBodyText(),
                message.getStatus(),
                message.getAttemptCount(),
                message.getScheduledAt(),
                message.getSentAt(),
                message.getCreatedAt()
        );
    }
}
