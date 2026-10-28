package edu.upb.semillero.repository.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Bitácora común para cambios importantes y accesos rechazados. Es compartida:
 * la crea torneos (punto 8 del acuerdo de la S1-04) pero sirve a los cuatro
 * módulos, por eso lleva la columna {@code modulo} y vive fuera de
 * {@code entity/torneos}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "auditoria", indexes = {
        @Index(name = "idx_auditoria_tabla", columnList = "tabla, registro_id"),
        @Index(name = "idx_auditoria_fecha", columnList = "fecha")
})
public class Auditoria extends BaseEntity {

    /** TORNEOS, FICHA, RENDIMIENTO, SCOUTING. */
    @Column(name = "modulo", length = 20, nullable = false)
    private String modulo;

    @Column(name = "tabla", length = 50, nullable = false)
    private String tabla;

    @Column(name = "registro_id")
    private Long registroId;

    /** CREAR, MODIFICAR, CAMBIO_ESTADO, REABRIR, ACCESO_DENEGADO... */
    @Column(name = "accion", length = 30, nullable = false)
    private String accion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "valor_anterior")
    private String valorAnterior;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "valor_nuevo")
    private String valorNuevo;

    @Column(name = "motivo", length = 255)
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id")
    private Usuario usuario;

    @Column(name = "fecha", nullable = false)
    @ColumnDefault("now()")
    private LocalDateTime fecha;
}
