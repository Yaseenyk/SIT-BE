package org.aisa.api.common;

/**
 * A dependency this endpoint needs is not configured or not reachable. Mapped to 503.
 *
 * <p>Exists so services can signal it without importing a web type. The obvious shortcut —
 * throwing Spring's {@code ResponseStatusException} from the service — put an HTTP concern
 * in the service layer AND was silently swallowed by the catch-all handler, which turned a
 * deliberate 503 into a 500.
 */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }
}
