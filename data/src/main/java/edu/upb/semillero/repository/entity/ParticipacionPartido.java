package edu.upb.semillero.repository.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "participaciones_partido",
        uniqueConstraints = @UniqueConstraint(name = "uk_participacion_jugador", columnNames = {"partidos_id", "jugadores_id"}),
        indexes = @Index(name = "idx_participacion_club", columnList = "partidos_id, clubes_id"))
public class ParticipacionPartido extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partidos_id", nullable = false)
    private Partido partido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jugadores_id", nullable = false)
    private Jugador jugador;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clubes_id", nullable = false)
    private Club club;

    @Column(name = "es_titular", nullable = false)
    private Boolean esTitular;

    @Column(name = "dorsal")
    private Short dorsal;

    @Column(name = "minuto_entrada", nullable = false)
    @ColumnDefault("0")
    private Short minutoEntrada;

    @Column(name = "minuto_salida")
    private Short minutoSalida;

    @Column(name = "velocidad_prom")
    private Double velocidadProm;

    @Column(name = "distancia_recorrida")
    private Double distanciaRecorrida;
}
