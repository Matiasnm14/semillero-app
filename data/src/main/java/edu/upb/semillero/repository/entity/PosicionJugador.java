package edu.upb.semillero.repository.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "posiciones_jugador")
public class PosicionJugador extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posiciones_id")
    private Posicion posicion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jugadores_id")
    private Jugador jugador;
}
