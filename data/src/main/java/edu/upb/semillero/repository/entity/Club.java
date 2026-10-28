package edu.upb.semillero.repository.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "clubes")
public class Club extends BaseEntity {

    @Column(name = "nombre_club", length = 15)
    private String nombreClub;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ciudad_id")
    private Ciudad ciudad;

    @Column(name = "foto_club")
    private String fotoClub;

    @Column(name = "anio_fundacion")
    private Integer anioFundacion;

    @Column(name = "descripcion_club", length = 500)
    private String descripcionClub;
}
