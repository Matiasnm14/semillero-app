package edu.upb.semillero.repository.entity.scouting;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity @Getter @Setter
@Table(name = "scouting_guardado", uniqueConstraints =
    @UniqueConstraint(name = "uk_sc_guardado", columnNames = {"clubId", "jugadorId"}))
public class JugadorGuardado {
    @Id private UUID id = UUID.randomUUID();
    @Column(nullable = false) private Long clubId;
    @Column(nullable = false) private Long jugadorId;
    @Column(nullable = false) private Long creadoPor;
    @Column(nullable = false) private Instant creadoEn;
}
