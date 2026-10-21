package edu.upb.semillero.repository.entity.torneos;

import edu.upb.semillero.repository.entity.BaseEntity;
import edu.upb.semillero.repository.entity.Partido;
import edu.upb.semillero.repository.entity.Usuario;
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
@Table(name = "reprogramaciones")
public class Reprogramacion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partidos_id", nullable = false)
    private Partido partido;

    @Column(name = "fecha_inicio_anterior")
    private LocalDateTime fechaInicioAnterior;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canchas_anterior_id")
    private Cancha canchaAnterior;

    @Column(name = "fecha_inicio_nueva", nullable = false)
    private LocalDateTime fechaInicioNueva;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "canchas_nueva_id", nullable = false)
    private Cancha canchaNueva;

    @Column(name = "motivo", length = 255, nullable = false)
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "users_id", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha", nullable = false)
    @ColumnDefault("now()")
    private LocalDateTime fecha;
}
