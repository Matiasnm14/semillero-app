package edu.upb.semillero.service.exception;

/** Raised when a session is not allowed to read or change private highlights. */
public class HighlightAccessDeniedException extends RuntimeException {
    public HighlightAccessDeniedException(String message) {
        super(message);
    }
}
