package edu.upb.semillero.repository.entity;

import edu.upb.semillero.repository.entity.torneos.Cancha;
import edu.upb.semillero.repository.entity.torneos.Grupo;
import edu.upb.semillero.repository.entity.torneos.TorneoCategoria;
import edu.upb.semillero.repository.enums.EstadoPartido;
import edu.upb.semillero.repository.enums.FaseTorneo;
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
        @Index(name = "idx_partidos_visitante", columnList = "club_visitante_id"),
        @Index(name = "idx_partidos_tc_estado", columnList = "torneo_categorias_id, estado"),
        @Index(name = "idx_partidos_cancha", columnList = "canchas_id, fecha_inicio")
}, check = {
        @CheckConstraint(name = "chk_clubes_distintos", constraint = "club_local_id <> club_visitante_id"),
        @CheckConstraint(name = "chk_partido_torneo_fase", constraint = "torneo_categorias_id IS NULL OR fase IS NOT NULL"),
        @CheckConstraint(name = "chk_walkover_club", constraint = "ganador_walkover_id IS NULL OR ganador_walkover_id IN (club_local_id, club_visitante_id)"),
        @CheckConstraint(name = "chk_partido_fechas", constraint = "fecha_fin IS NULL OR fecha_fin > fecha_inicio"),
        @CheckConstraint(name = "chk_partido_goles_local", constraint = "goles_local >= 0"),
        @CheckConstraint(name = "chk_partido_goles_visitante", constraint = "goles_visitante >= 0"),
        @CheckConstraint(name = "chk_partido_penales_local", constraint = "penales_local >= 0"),
        @CheckConstraint(name = "chk_partido_penales_visitante", constraint = "penales_visitante >= 0")
})
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

    // Columnas que agrega el módulo de Torneos (tarea S1-06). Todas opcionales,
    // así el CRUD que ya existía sigue funcionando igual. Partido cerrado =
    // FINALIZADO; ganado por W.O. = FINALIZADO con ganadorWalkover; anulado =
    // CANCELADO. No se agregan estados nuevos.

    /** NULL = partido fuera de torneo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "torneo_categorias_id")
    private TorneoCategoria torneoCategoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupos_id")
    private Grupo grupo;

    @Enumerated(EnumType.STRING)
    @Column(name = "fase", length = 15)
    private FaseTorneo fase;

    /** Número de fecha dentro del fixture. */
    @Column(name = "jornada")
    private Short jornada;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canchas_id")
    private Cancha cancha;

    @Column(name = "arbitro", length = 100)
    private String arbitro;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mesa_control_id")
    private Usuario mesaControl;

    @Column(name = "goles_local")
    private Short golesLocal;

    @Column(name = "goles_visitante")
    private Short golesVisitante;

    @Column(name = "penales_local")
    private Short penalesLocal;

    @Column(name = "penales_visitante")
    private Short penalesVisitante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ganador_walkover_id")
    private Club ganadorWalkover;

    /** Llave de eliminación directa: a qué partido pasa el ganador. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partido_siguiente_id")
    private Partido partidoSiguiente;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cerrado_por")
    private Usuario cerradoPor;
}
