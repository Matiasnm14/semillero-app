package edu.upb.semillero.repository.entity.torneos;

import edu.upb.semillero.repository.entity.BaseEntity;
import edu.upb.semillero.repository.entity.Ciudad;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "sedes")
public class Sede extends BaseEntity {

    @Column(name = "nombre", length = 80, nullable = false)
    private String nombre;

    @Column(name = "direccion", length = 150, nullable = false)
    private String direccion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ciudad_id", nullable = false)
    private Ciudad ciudad;
}
