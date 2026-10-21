package edu.upb.semillero.repository.entity.torneos;

import edu.upb.semillero.repository.entity.BaseEntity;
import edu.upb.semillero.repository.entity.Partido;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.enums.torneos.EstadoPlanilla;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

/**
 * Cabecera de la planilla del partido. Los jugadores de la planilla se guardan
 * en participaciones_partido, la tabla acordada entre grupos.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "planillas",
        uniqueConstraints = @UniqueConstraint(name = "uk_planilla_partido", columnNames = "partidos_id"))
public class Planilla extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partidos_id", nullable = false)
    private Partido partido;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 15, nullable = false)
    @ColumnDefault("'BORRADOR'")
    private EstadoPlanilla estado;

    @Column(name = "observaciones_arbitro", columnDefinition = "text")
    private String observacionesArbitro;

    /** Mesa de control que llena la planilla. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "users_id", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha_confirmacion")
    private LocalDateTime fechaConfirmacion;
}
