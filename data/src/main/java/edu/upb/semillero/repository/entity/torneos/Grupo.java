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
@Table(name = "grupos",
        uniqueConstraints = @UniqueConstraint(name = "uk_grupo_categoria_nombre", columnNames = {"torneo_categorias_id", "nombre"}))
public class Grupo extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "torneo_categorias_id", nullable = false)
    private TorneoCategoria torneoCategoria;

    @Column(name = "nombre", length = 10, nullable = false)
    private String nombre;
}
