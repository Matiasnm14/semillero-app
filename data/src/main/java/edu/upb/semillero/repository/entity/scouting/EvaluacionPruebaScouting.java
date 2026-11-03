package edu.upb.semillero.repository.entity.scouting;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scouting_evaluacion_prueba")
@Getter
@Setter
public class EvaluacionPruebaScouting {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID inscripcionId;

    @Column(nullable = false)
    private Long evaluadorId;

    @Column(nullable = false)
    private Instant fecha;

    @Column(nullable = false, length = 20)
    private String asistencia;

    @Column(nullable = false, length = 24)
    private String resultado;

    @Column(nullable = false, length = 1000)
    private String observaciones;
}
