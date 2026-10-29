package edu.upb.semillero.repository.entity.scouting;

import edu.upb.semillero.repository.enums.scouting.EstadoContacto;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity @Getter @Setter
@Table(name = "scouting_solicitud", uniqueConstraints =
    @UniqueConstraint(name = "uk_sc_solicitud_idempotencia", columnNames = {"solicitanteId", "clave"}))
public class SolicitudContacto {
    @Id private UUID id = UUID.randomUUID();
    @Column(nullable = false, updatable = false) private Long clubId;
    @Column(nullable = false, updatable = false) private Long jugadorId;
    @Column(nullable = false, updatable = false) private Long solicitanteId;
    @Column(nullable = false, updatable = false) private UUID clave;
    @Column(nullable = false, length = 1000, updatable = false) private String motivo;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 15)
    private EstadoContacto estado = EstadoContacto.PENDIENTE;
    @Column(nullable = false, updatable = false) private Instant creadaEn;
    @Column(nullable = false) private Instant actualizadaEn;
    @Version private long version;
}
