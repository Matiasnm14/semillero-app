package edu.upb.semillero.repository.entity.torneos;

import edu.upb.semillero.repository.entity.BaseEntity;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.enums.torneos.AmbitoTorneo;
import edu.upb.semillero.repository.enums.torneos.EstadoTorneo;
import edu.upb.semillero.repository.enums.torneos.FormatoTorneo;
import edu.upb.semillero.repository.enums.torneos.TipoTorneo;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "torneos", check = {
        @CheckConstraint(name = "chk_torneo_fechas", constraint = "fecha_fin >= fecha_inicio"),
        @CheckConstraint(name = "chk_torneo_limite_inscripcion", constraint = "fecha_limite_inscripcion <= fecha_inicio"),
        @CheckConstraint(name = "chk_torneo_organizador_externo", constraint = "ambito = 'INTERNO' OR organizador_externo IS NOT NULL")
})
public class Torneo extends BaseEntity {

    @Column(name = "nombre", length = 100, nullable = false)
    private String nombre;

    @Column(name = "temporada", nullable = false)
    private Short temporada;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 15, nullable = false)
    private TipoTorneo tipo;

    /** EXTERNO = campeonato de otra institución; exige organizadorExterno. */
    @Enumerated(EnumType.STRING)
    @Column(name = "ambito", length = 10, nullable = false)
    @ColumnDefault("'INTERNO'")
    private AmbitoTorneo ambito;

    @Column(name = "organizador_externo", length = 100)
    private String organizadorExterno;

    @Enumerated(EnumType.STRING)
    @Column(name = "formato", length = 25, nullable = false)
    private FormatoTorneo formato;

    @Column(name = "ida_vuelta", nullable = false)
    @ColumnDefault("false")
    private Boolean idaVuelta;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Column(name = "fecha_limite_inscripcion", nullable = false)
    private LocalDate fechaLimiteInscripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 25, nullable = false)
    @ColumnDefault("'BORRADOR'")
    private EstadoTorneo estado;

    /** Organizador que creó el torneo. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "users_id", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha_creacion", nullable = false)
    @ColumnDefault("now()")
    private LocalDateTime fechaCreacion;
}
