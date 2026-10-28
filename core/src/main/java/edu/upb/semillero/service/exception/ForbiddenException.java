package edu.upb.semillero.service.exception;

/**
 * El usuario tiene sesión pero no puede tocar <em>ese</em> registro: el
 * entrenador de otro club, la mesa de control de otro partido. Es la tarea
 * S2-03, y se traduce a un 403.
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
