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

    @Lob
    @Column(name = "foto_club")
    private String fotoClub;
}
