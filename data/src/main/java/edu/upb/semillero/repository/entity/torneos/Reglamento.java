package edu.upb.semillero.repository.entity.torneos;

import edu.upb.semillero.repository.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "reglamentos",
        uniqueConstraints = @UniqueConstraint(name = "uk_reglamento_torneo", columnNames = "torneos_id"),
        check = @CheckConstraint(name = "chk_reglamento_jugadores", constraint = "min_jugadores <= max_jugadores"))
public class Reglamento extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "torneos_id", nullable = false)
    private Torneo torneo;

    @Column(name = "puntos_victoria", nullable = false)
    @ColumnDefault("3")
    private Short puntosVictoria;

    @Column(name = "puntos_empate", nullable = false)
    @ColumnDefault("1")
    private Short puntosEmpate;

    @Column(name = "puntos_derrota", nullable = false)
    @ColumnDefault("0")
    private Short puntosDerrota;

    @Column(name = "umbral_amarillas", nullable = false)
    @ColumnDefault("3")
    private Short umbralAmarillas;

    @Column(name = "fechas_suspension_amarillas", nullable = false)
    @ColumnDefault("1")
    private Short fechasSuspensionAmarillas;

    @Column(name = "fechas_suspension_roja", nullable = false)
    @ColumnDefault("1")
    private Short fechasSuspensionRoja;

    @Column(name = "goles_walkover", nullable = false)
    @ColumnDefault("3")
    private Short golesWalkover;

    /** Fecha a la que se calcula la edad; por defecto 31/12 de la temporada. */
    @Column(name = "fecha_corte", nullable = false)
    private LocalDate fechaCorte;

    @Column(name = "min_jugadores", nullable = false)
    private Short minJugadores;

    @Column(name = "max_jugadores", nullable = false)
    private Short maxJugadores;

    /** Minutos de descanso entre partidos del mismo equipo. */
    @Column(name = "descanso_minimo_min", nullable = false)
    @ColumnDefault("60")
    private Short descansoMinimoMin;
}
