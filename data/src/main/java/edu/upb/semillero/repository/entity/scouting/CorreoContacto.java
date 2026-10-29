package edu.upb.semillero.repository.entity.scouting;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Bandeja transaccional. Esta entrega no despacha correos ni los marca como enviados. */
@Entity @Table(name = "scouting_correo", uniqueConstraints =
    @UniqueConstraint(name = "uk_sc_correo_evento", columnNames = {"evento_id", "destinatarioId"}))
@Getter @Setter
public class CorreoContacto {
    @Id private UUID id = UUID.randomUUID();
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false) private EventoContacto evento;
    @Column(nullable = false) private Long destinatarioId;
    @Column(nullable = false, length = 30) private String plantilla;
    @Column(nullable = false, length = 30) private String estado = "PENDIENTE_CONFIGURACION";
    @Column(nullable = false) private Instant creadaEn;
}
