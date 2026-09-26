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

    /**
     * Dispatches a payment success and PRO plan activation email with invoice receipt.
     *
     * @param toEmail       recipient email address
     * @param userName      recipient name
     * @param invoiceNumber invoice number
     * @param amount        formatted amount (e.g. $50.00)
     * @param plan          plan name (e.g. PRO)
     * @param date          formatted transaction date
     * @param paymentId     Razorpay payment ID
     */
    void sendPaymentSuccessEmail(String toEmail, String userName, String invoiceNumber, String amount, String plan, String date, String paymentId);

    /**
     * Dispatches a payment failure alert notification.
     *
     * @param toEmail   recipient email address
     * @param userName  recipient name
     * @param amount    formatted amount (e.g. $50.00)
     * @param plan      plan name
     * @param date      formatted transaction date
     * @param reason    failure reason or description
     */
    void sendPaymentFailedEmail(String toEmail, String userName, String amount, String plan, String date, String reason);
}
