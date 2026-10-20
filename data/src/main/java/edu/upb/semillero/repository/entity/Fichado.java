package edu.upb.semillero.repository.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "fichado")
public class Fichado extends BaseEntity {

    @Column(name = "fecha_fichaje")
    private LocalDateTime fechaFichaje;

    @Column(name = "fecha_final_fichaje")
    private LocalDateTime fechaFinalFichaje;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jugadores_id")
    private Jugador jugador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clubes_id")
    private Club club;
}
