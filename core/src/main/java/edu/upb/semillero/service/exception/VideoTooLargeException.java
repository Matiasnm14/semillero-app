package edu.upb.semillero.service.exception;

public class VideoTooLargeException extends RuntimeException {
    public VideoTooLargeException() {
        super("El video supera el tamaño máximo permitido");
    }
}
