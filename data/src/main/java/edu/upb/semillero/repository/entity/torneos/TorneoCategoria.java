package edu.upb.semillero.repository.entity.torneos;

import edu.upb.semillero.repository.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "torneo_categorias",
        uniqueConstraints = @UniqueConstraint(name = "uk_torneo_categoria_nombre", columnNames = {"torneos_id", "nombre"}),
        check = {
                @CheckConstraint(name = "chk_torneo_categoria_cupo", constraint = "cupo_maximo >= 2"),
                @CheckConstraint(name = "chk_torneo_categoria_edades", constraint = "edad_minima IS NULL OR edad_minima <= edad_maxima")
        })
public class TorneoCategoria extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "torneos_id", nullable = false)
    private Torneo torneo;

    /** Sub-12, Sub-14, Categoría 2014... */
    @Column(name = "nombre", length = 20, nullable = false)
    private String nombre;

    /** Edad cumplida a la fecha de corte del reglamento. */
    @Column(name = "edad_minima")
    private Short edadMinima;

    @Column(name = "edad_maxima", nullable = false)
    private Short edadMaxima;

    @Column(name = "cupo_maximo", nullable = false)
    private Short cupoMaximo;

    /** Duración de cada tiempo, en minutos. */
    @Column(name = "duracion_tiempo_min", nullable = false)
    private Short duracionTiempoMin;

    @Column(name = "jugadores_en_cancha", nullable = false)
    private Short jugadoresEnCancha;

    @Column(name = "max_sustituciones")
    private Short maxSustituciones;
}
