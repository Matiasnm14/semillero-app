package edu.upb.semillero.repository.entity.torneos;

import edu.upb.semillero.repository.entity.BaseEntity;
import edu.upb.semillero.repository.entity.Partido;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.enums.torneos.EstadoSancion;
import edu.upb.semillero.repository.enums.torneos.OrigenSancion;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "sanciones",
        indexes = @Index(name = "idx_sanciones_estado", columnList = "estado"),
        check = {
                @CheckConstraint(name = "chk_sancion_fechas_suspension", constraint = "fechas_suspension > 0"),
                @CheckConstraint(name = "chk_sancion_fechas_cumplidas", constraint = "fechas_cumplidas BETWEEN 0 AND fechas_suspension"),
                @CheckConstraint(name = "chk_sancion_manual_motivo", constraint = "origen <> 'MANUAL' OR motivo IS NOT NULL")
        })
public class Sancion extends BaseEntity {

    /** El jugador dentro del torneo. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lista_buena_fe_id", nullable = false)
    private ListaBuenaFe listaBuenaFe;

    /** Partido que originó la sanción. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partidos_id")
    private Partido partido;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen", length = 25, nullable = false)
    private OrigenSancion origen;

    @Column(name = "fechas_suspension", nullable = false)
    private Short fechasSuspension;

    @Column(name = "fechas_cumplidas", nullable = false)
    @ColumnDefault("0")
    private Short fechasCumplidas;

    @Column(name = "motivo", length = 255)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 15, nullable = false)
    @ColumnDefault("'VIGENTE'")
    private EstadoSancion estado;

    /** Nulo cuando la sanción la genera el sistema. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id")
    private Usuario usuario;

    @Column(name = "fecha_registro", nullable = false)
    @ColumnDefault("now()")
    private LocalDateTime fechaRegistro;
}
