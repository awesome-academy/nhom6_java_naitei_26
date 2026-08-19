package com.example.hotelmanagement.services;

public interface EmailService {
    void sendVerificationEmail(String toEmail, String token);
    void sendPasswordResetEmail(String toEmail, String token);
}
