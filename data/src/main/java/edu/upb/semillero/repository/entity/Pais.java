package edu.upb.semillero.repository.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "pais")
public class Pais extends BaseEntity {

    @Column(name = "nombre_pais", length = 30)
    private String nombrePais;
}
