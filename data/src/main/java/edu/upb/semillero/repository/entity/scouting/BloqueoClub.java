package edu.upb.semillero.repository.entity.scouting;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "scouting_bloqueo") @Getter @Setter
public class BloqueoClub {
    @Id @Column(length = 80) private String id;
    @Column(nullable = false) private Long jugadorId;
    @Column(nullable = false) private Long clubId;
    @Column(nullable = false) private boolean activo;
    @Column(nullable = false) private Instant bloqueadoEn;
    private Instant desbloqueadoEn;
}
