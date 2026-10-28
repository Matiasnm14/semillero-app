package edu.upb.semillero.repository.entity.torneos;

import edu.upb.semillero.repository.entity.BaseEntity;
import edu.upb.semillero.repository.entity.Club;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.enums.torneos.EstadoInscripcion;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

/**
 * El "equipo" del torneo: un club inscrito en una categoría. No hay tabla
 * equipos.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "inscripciones_equipo",
        uniqueConstraints = @UniqueConstraint(name = "uk_inscripcion_categoria_club", columnNames = {"torneo_categorias_id", "clubes_id"}),
        check = @CheckConstraint(name = "chk_inscripcion_motivo", constraint = "estado NOT IN ('OBSERVADA','RECHAZADA') OR motivo IS NOT NULL"))
public class InscripcionEquipo extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "torneo_categorias_id", nullable = false)
    private TorneoCategoria torneoCategoria;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clubes_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupos_id")
    private Grupo grupo;

    @Column(name = "cabeza_serie")
    private Short cabezaSerie;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 15, nullable = false)
    @ColumnDefault("'PENDIENTE'")
    private EstadoInscripcion estado;

    @Column(name = "motivo", length = 255)
    private String motivo;

    @Column(name = "lista_confirmada", nullable = false)
    @ColumnDefault("false")
    private Boolean listaConfirmada;

    /** Entrenador que inscribe al equipo. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "users_id", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha_inscripcion", nullable = false)
    @ColumnDefault("now()")
    private LocalDateTime fechaInscripcion;
}
