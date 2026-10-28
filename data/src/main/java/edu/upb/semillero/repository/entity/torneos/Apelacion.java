package edu.upb.semillero.repository.entity.torneos;

import edu.upb.semillero.repository.entity.BaseEntity;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.enums.torneos.EstadoApelacion;
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
@Table(name = "apelaciones")
public class Apelacion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sanciones_id", nullable = false)
    private Sancion sancion;

    @Column(name = "motivo", length = 255, nullable = false)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 15, nullable = false)
    @ColumnDefault("'PENDIENTE'")
    private EstadoApelacion estado;

    @Column(name = "resolucion", length = 255)
    private String resolucion;

    @Column(name = "fechas_resueltas")
    private Short fechasResueltas;

    /** Quien presenta la apelación. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "users_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resuelto_por")
    private Usuario resueltoPor;

    @Column(name = "fecha_presentacion", nullable = false)
    @ColumnDefault("now()")
    private LocalDateTime fechaPresentacion;

    @Column(name = "fecha_resolucion")
    private LocalDateTime fechaResolucion;
}
