package com.mentorhub.mail;

/**
 * A message was not accepted by the mail server. The message describes the failure on one line and never contains the
 * message's text; it may contain the recipient address and the mail server's reply.
 */
public class MailDeliveryException extends RuntimeException {

    public MailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
