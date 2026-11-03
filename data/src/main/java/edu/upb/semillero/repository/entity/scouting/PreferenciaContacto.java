package edu.upb.semillero.repository.entity.scouting;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Opt-in del titular. IDs externos se conservan sin escribir las tablas de ficha. */
@Entity @Table(name = "scouting_preferencia") @Getter @Setter
public class PreferenciaContacto {
    @Id private Long jugadorId;
    @Column(nullable = false) private boolean visible;
    @Column(nullable = false) private boolean aceptaSolicitudes;
    @Column(nullable = false) private Instant actualizadaEn;
    @Version private long version;
}
