package edu.upb.semillero.repository.entity;

import edu.upb.semillero.repository.enums.EstadoInscripcionPrueba;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "inscripciones_prueba", uniqueConstraints =
        @UniqueConstraint(name = "uq_inscripcion_prueba_jugador", columnNames = {"prueba_id", "jugador_id"}),
        indexes = @Index(name = "idx_inscripciones_prueba_prueba_estado", columnList = "prueba_id, estado"))
public class InscripcionPrueba extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prueba_id", nullable = false)
    private Prueba prueba;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jugador_id", nullable = false)
    private Jugador jugador;

    @Column(name = "fecha_postulacion", nullable = false)
    private LocalDateTime fechaPostulacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 20, nullable = false)
    private EstadoInscripcionPrueba estado = EstadoInscripcionPrueba.PENDIENTE;

    @Column(name = "nota", length = 500)
    private String nota;

    @Column(name = "nota_club", length = 500)
    private String notaClub;
}
