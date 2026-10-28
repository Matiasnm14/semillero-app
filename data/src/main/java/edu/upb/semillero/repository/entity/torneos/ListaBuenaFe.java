package edu.upb.semillero.repository.entity.torneos;

import edu.upb.semillero.repository.entity.BaseEntity;
import edu.upb.semillero.repository.entity.Jugador;
import edu.upb.semillero.repository.entity.Posicion;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

/**
 * Lista de buena fe por campeonato, interno o externo. El torneo lo copia el
 * servicio desde la inscripción, para que un jugador esté en un solo equipo por
 * torneo.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "lista_buena_fe", uniqueConstraints = {
        @UniqueConstraint(name = "uk_lista_bf_dorsal", columnNames = {"inscripciones_equipo_id", "dorsal"}),
        @UniqueConstraint(name = "uk_lista_bf_jugador_torneo", columnNames = {"torneos_id", "jugadores_id"})
}, indexes = @Index(name = "idx_lista_bf_jugador", columnList = "jugadores_id"),
        check = @CheckConstraint(name = "chk_lista_bf_dorsal", constraint = "dorsal BETWEEN 1 AND 99"))
public class ListaBuenaFe extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inscripciones_equipo_id", nullable = false)
    private InscripcionEquipo inscripcionEquipo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "torneos_id", nullable = false)
    private Torneo torneo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jugadores_id", nullable = false)
    private Jugador jugador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posiciones_id")
    private Posicion posicion;

    @Column(name = "dorsal", nullable = false)
    private Short dorsal;

    @Column(name = "habilitado", nullable = false)
    @ColumnDefault("true")
    private Boolean habilitado;

    @Column(name = "motivo_inhabilitacion", length = 255)
    private String motivoInhabilitacion;

    @Column(name = "fecha_alta", nullable = false)
    @ColumnDefault("now()")
    private LocalDateTime fechaAlta;
}
