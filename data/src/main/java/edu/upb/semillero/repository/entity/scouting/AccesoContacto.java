package edu.upb.semillero.repository.entity.scouting;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name = "scouting_acceso") @Getter @Setter
public class AccesoContacto {
    @Id private UUID id = UUID.randomUUID();
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitud_id", nullable = false) private SolicitudContacto solicitud;
    @Column(nullable = false) private Long actorId;
    @Column(nullable = false, length = 300) private String proposito;
    @Column(nullable = false) private Instant fecha;
}
