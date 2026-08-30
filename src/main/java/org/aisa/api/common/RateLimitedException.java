package org.aisa.api.common;

/** Too many attempts from one source. Mapped to 429. */
public class RateLimitedException extends RuntimeException {

    public RateLimitedException(String message) {
        super(message);
    }
}
