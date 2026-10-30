package com.mentorhub.mail;

/** Sends plain text emails from the configured sender address through the configured mail server. */
public interface MailDelivery {

    /** Whether a mail server is configured; fixed for the application's lifetime. */
    boolean isEnabled();

    /**
     * Sends synchronously: returns once the mail server has accepted the message, within the configured mail
     * timeouts. Holds no database resources; call it outside transactions.
     *
     * @throws MailDeliveryException when the mail server refuses the message or cannot be reached, or the recipient is
     *     not a valid address
     * @throws IllegalStateException when email is not enabled
     */
    void send(MailMessage message);
}
