package edu.upb.semillero.repository.entity;

import edu.upb.semillero.repository.enums.EstadoPartido;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "partidos", indexes = {
        @Index(name = "idx_partidos_fecha_inicio", columnList = "fecha_inicio"),
        @Index(name = "idx_partidos_local", columnList = "club_local_id"),
        @Index(name = "idx_partidos_visitante", columnList = "club_visitante_id")
}, check = @CheckConstraint(name = "chk_clubes_distintos", constraint = "club_local_id <> club_visitante_id"))
public class Partido extends BaseEntity {

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_local_id", nullable = false)
    private Club clubLocal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_visitante_id", nullable = false)
    private Club clubVisitante;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 20, nullable = false)
    private EstadoPartido estado;
}
