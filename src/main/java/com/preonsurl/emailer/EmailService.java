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
}
