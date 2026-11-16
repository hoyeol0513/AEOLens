package com.aeolens.api.audit;

/**
 * The requested audit target is not acceptable (malformed, unresolvable or non-public).
 * Mapped to HTTP 400 with the message shown to the user.
 */
public class InvalidAuditTargetException extends IllegalArgumentException {

    public InvalidAuditTargetException(String message) {
        super(message);
    }

    public InvalidAuditTargetException(String message, Throwable cause) {
        super(message, cause);
    }
}
