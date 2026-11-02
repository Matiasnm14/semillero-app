package edu.upb.semillero.repository.entity;

import edu.upb.semillero.repository.enums.EstadoDocumentoJugador;
import edu.upb.semillero.repository.enums.TipoDocumentoJugador;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

/**
 * Documento de un jugador (CI, certificado de nacimiento, apto médico,
 * autorización de padres, seguro). Tabla compartida que mantiene ficha técnica
 * (punto 7 del acuerdo S1-04); torneos solo la lee para la lista de buena fe.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "documentos_jugador",
        indexes = @Index(name = "idx_documentos_jugador_jugador", columnList = "jugadores_id, tipo"))
public class DocumentoJugador extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jugadores_id", nullable = false)
    private Jugador jugador;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 25, nullable = false)
    private TipoDocumentoJugador tipo;

    @Column(name = "numero", length = 30)
    private String numero;

    @Column(name = "fecha_emision")
    private LocalDate fechaEmision;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 15, nullable = false)
    @ColumnDefault("'PENDIENTE'")
    private EstadoDocumentoJugador estado;
}
