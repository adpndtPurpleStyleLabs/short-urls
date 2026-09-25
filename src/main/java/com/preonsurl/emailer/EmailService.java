package com.preonsurl.emailer;

/**
 * Service interface for dispatching transactional and verification emails.
 */
public interface EmailService {

    /**
     * Dispatches a 6-digit email verification code to the specified user.
     *
     * @param toEmail       recipient email address
     * @param code          6-digit numeric verification code
     * @param recipientName recipient full name or username
     */
    void sendVerificationCode(String toEmail, String code, String recipientName);

    /**
     * Dispatches the welcome email upon successful account verification.
     *
     * @param toEmail   recipient email address
     * @param userName  recipient name (full name or username)
     */
    void sendWelcomeEmail(String toEmail, String userName);
}
