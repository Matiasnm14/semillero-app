package edu.upb.semillero.repository.entity.scouting;

import edu.upb.semillero.repository.enums.scouting.EstadoContacto;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Solo se insertan eventos; la API no ofrece actualización ni eliminación. */
@Entity @Table(name = "scouting_evento") @Getter @Setter
public class EventoContacto {
    @Id private UUID id = UUID.randomUUID();
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitud_id", nullable = false) private SolicitudContacto solicitud;
    @Column(nullable = false) private Long actorId;
    @Enumerated(EnumType.STRING) @Column(length = 15) private EstadoContacto anterior;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 15) private EstadoContacto nuevo;
    @Column(nullable = false, length = 500) private String motivo;
    @Column(nullable = false) private Instant fecha;
}
