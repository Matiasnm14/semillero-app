package edu.upb.semillero.service.scouting;

public class LimiteContactoException extends RuntimeException {
    private final long retryAfter;
    public LimiteContactoException(long retryAfter) {
        super("Se alcanzó el límite diario de solicitudes; intenta más tarde");
        this.retryAfter = retryAfter;
    }
    public long retryAfter() { return retryAfter; }
}
