package edu.upb.semillero.repository.enums;

public enum EstadoAnalisis {
    PENDIENTE, PROCESANDO, COMPLETADO, ERROR;

    public boolean isTerminal() {
        return this == COMPLETADO || this == ERROR;
    }
}
