package org.aisa.api.common;

/** A request that is well-formed but conflicts with current state. Mapped to 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
