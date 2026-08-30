package org.aisa.api.common;

/** Thrown by services when an id does not resolve. Mapped to 404 by GlobalExceptionHandler. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String what, Object id) {
        super(what + " " + id + " does not exist");
    }

    public NotFoundException(String message) {
        super(message);
    }
}
