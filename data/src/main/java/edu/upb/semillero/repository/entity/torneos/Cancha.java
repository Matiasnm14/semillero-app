package edu.upb.semillero.repository.entity.torneos;

import edu.upb.semillero.repository.entity.BaseEntity;
import edu.upb.semillero.repository.enums.torneos.TipoSuperficie;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "canchas",
        uniqueConstraints = @UniqueConstraint(name = "uk_cancha_sede_nombre", columnNames = {"sedes_id", "nombre"}))
public class Cancha extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sedes_id", nullable = false)
    private Sede sede;

    @Column(name = "nombre", length = 50, nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_superficie", length = 20, nullable = false)
    private TipoSuperficie tipoSuperficie;

    @Column(name = "largo_m", precision = 5, scale = 1)
    private BigDecimal largoM;

    @Column(name = "ancho_m", precision = 5, scale = 1)
    private BigDecimal anchoM;

    @Column(name = "techada", nullable = false)
    @ColumnDefault("false")
    private Boolean techada;

    @Column(name = "iluminacion", nullable = false)
    @ColumnDefault("false")
    private Boolean iluminacion;

    @Column(name = "vestuarios", nullable = false)
    @ColumnDefault("false")
    private Boolean vestuarios;

    @Column(name = "descripcion", length = 255)
    private String descripcion;

    @Column(name = "activa", nullable = false)
    @ColumnDefault("true")
    private Boolean activa;
}
