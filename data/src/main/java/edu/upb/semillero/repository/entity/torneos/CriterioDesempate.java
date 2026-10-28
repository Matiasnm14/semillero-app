package edu.upb.semillero.repository.entity.torneos;

import edu.upb.semillero.repository.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "criterios_desempate",
        uniqueConstraints = @UniqueConstraint(name = "uk_criterio_desempate_codigo", columnNames = "codigo"))
public class CriterioDesempate extends BaseEntity {

    @Column(name = "codigo", length = 20, nullable = false)
    private String codigo;

    @Column(name = "nombre", length = 50, nullable = false)
    private String nombre;
}
