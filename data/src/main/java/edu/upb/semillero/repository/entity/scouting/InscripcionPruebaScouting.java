package edu.upb.semillero.repository.entity.scouting;

import edu.upb.semillero.repository.entity.Jugador;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "scouting_inscripcion_prueba",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_sc_inscripcion",
                        columnNames = {"convocatoria_id", "jugador_id"}))
@Getter
@Setter
public class InscripcionPruebaScouting {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "convocatoria_id", nullable = false)
    private ConvocatoriaPrueba convocatoria;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jugador_id", nullable = false)
    private Jugador jugador;

    @Column(nullable = false, length = 24)
    private String estado = "PENDIENTE";

    @Column(length = 500)
    private String nota;

    @Column(nullable = false)
    private Instant creadaEn;

    @Column(nullable = false, length = 20)
    private String asistencia = "SIN_REGISTRAR";

    @Column(length = 1000)
    private String observaciones;

    private Instant evaluadaEn;
    @Version private Long version;
}
