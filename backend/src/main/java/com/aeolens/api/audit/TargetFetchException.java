package com.aeolens.api.audit;

/**
 * The target was valid but its response could not be used for an audit
 * (empty body, oversized page, broken or excessive redirects).
 * Mapped to HTTP 502 with the message shown to the user.
 */
public class TargetFetchException extends RuntimeException {

    public TargetFetchException(String message) {
        super(message);
    }

    public TargetFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
