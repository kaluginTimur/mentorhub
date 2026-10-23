package com.mentorhub.mail;

/**
 * A plain text email.
 *
 * @param to one recipient address
 * @param text the body; sent as UTF-8
 */
public record MailMessage(String to, String subject, String text) {}
