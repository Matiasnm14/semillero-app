package edu.upb.semillero.repository.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

/**
 * Notificaciones para la web y la app móvil. Compartida: la crea torneos
 * (punto 9 del acuerdo de la S1-04) y la columna {@code modulo} deja que la
 * usen los cuatro módulos.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notificaciones",
        indexes = @Index(name = "idx_notificaciones_usuario", columnList = "users_id, leida"))
public class Notificacion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "users_id", nullable = false)
    private Usuario usuario;

    @Column(name = "modulo", length = 20, nullable = false)
    private String modulo;

    @Column(name = "titulo", length = 100, nullable = false)
    private String titulo;

    @Column(name = "mensaje", length = 500, nullable = false)
    private String mensaje;

    @Column(name = "leida", nullable = false)
    @ColumnDefault("false")
    private Boolean leida = false;

    @Column(name = "fecha", nullable = false)
    @ColumnDefault("now()")
    private LocalDateTime fecha;
}
