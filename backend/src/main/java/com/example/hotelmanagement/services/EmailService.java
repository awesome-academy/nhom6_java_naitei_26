package com.example.hotelmanagement.services;

import com.example.hotelmanagement.entity.Booking;
import com.example.hotelmanagement.entity.Payment;
import com.example.hotelmanagement.entity.Refund;
import com.example.hotelmanagement.entity.User;

public interface EmailService {

    void sendVerificationEmail(String toEmail, String fullName, String token);

    void sendStaffInvitationEmail(String toEmail, String fullName, String token, String temporaryPassword);

    /** @deprecated Staff invitations must include the Admin-provided temporary password. */
    @Deprecated
    default void sendStaffInvitationEmail(String toEmail, String fullName, String token) {
        sendStaffInvitationEmail(toEmail, fullName, token, "");
    }

    void sendPasswordResetEmail(String toEmail, String fullName, String token);

    void sendAccountActivatedEmail(User user);

    void sendBookingConfirmedEmail(Booking booking);

    void sendBookingCancelledEmail(Booking booking);

    void sendPaymentSuccessEmail(Payment payment);

    void sendPaymentRefundEmail(Refund refund);
}
