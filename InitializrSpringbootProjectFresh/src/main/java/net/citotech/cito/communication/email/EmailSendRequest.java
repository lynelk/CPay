package net.citotech.cito.communication.email;

/**
 * Immutable email send request (ISO domain mapping: communication/email, track B2). Mirrors the
 * fields the legacy {@code SendMail} path passed to {@code SimpleMailMessage} (to, subject, plain
 * text body) so the new Spring Mail delivery service can replace the ad-hoc sender without changing
 * call-site semantics.
 */
public record EmailSendRequest(String to, String subject, String body) {}
