package edu.upb.semillero.repository.entity.torneos;

import edu.upb.semillero.repository.entity.BaseEntity;
import edu.upb.semillero.repository.entity.Club;
import edu.upb.semillero.repository.entity.ParticipacionPartido;
import edu.upb.semillero.repository.entity.Partido;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.enums.torneos.TipoEventoPartido;
import edu.upb.semillero.repository.enums.torneos.TipoGol;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

/**
 * Eventos del partido. Apuntan a participaciones_partido, no a jugadores.
 *
 * <p>El CHECK {@code (tipo = 'GOL') = (tipo_gol IS NOT NULL)} del script está
 * partido en dos CHECK equivalentes, para no comparar dos predicados booleanos
 * entre sí (H2 y PostgreSQL lo escriben distinto).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "eventos_partido",
        indexes = @Index(name = "idx_eventos_partido", columnList = "partidos_id"),
        check = {
                @CheckConstraint(name = "chk_evento_minuto", constraint = "minuto BETWEEN 0 AND 130"),
                @CheckConstraint(name = "chk_evento_gol_exige_tipo", constraint = "tipo <> 'GOL' OR tipo_gol IS NOT NULL"),
                @CheckConstraint(name = "chk_evento_no_gol_sin_tipo", constraint = "tipo = 'GOL' OR tipo_gol IS NULL"),
                @CheckConstraint(name = "chk_evento_cambio_exige_rel", constraint = "tipo <> 'CAMBIO' OR participacion_rel_id IS NOT NULL"),
                @CheckConstraint(name = "chk_evento_participaciones_distintas", constraint = "participacion_rel_id IS DISTINCT FROM participaciones_partido_id")
        })
public class EventoPartido extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partidos_id", nullable = false)
    private Partido partido;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 10, nullable = false)
    private TipoEventoPartido tipo;

    @Column(name = "minuto", nullable = false)
    private Short minuto;

    /** Autor del gol, amonestado, o el jugador que sale en un cambio. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participaciones_partido_id", nullable = false)
    private ParticipacionPartido participacion;

    /** Asistencia del gol, o el jugador que entra en un cambio. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participacion_rel_id")
    private ParticipacionPartido participacionRelacionada;

    /** Equipo al que suma el evento. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clubes_id", nullable = false)
    private Club club;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_gol", length = 12)
    private TipoGol tipoGol;

    @Column(name = "motivo", length = 150)
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "users_id", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha_registro", nullable = false)
    @ColumnDefault("now()")
    private LocalDateTime fechaRegistro;
}
