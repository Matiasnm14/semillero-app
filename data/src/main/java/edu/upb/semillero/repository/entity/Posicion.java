package edu.upb.semillero.repository.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "posiciones")
public class Posicion extends BaseEntity {

    @Column(name = "posicion", length = 100)
    private String posicion;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "codigo_posicion", length = 4, columnDefinition = "char(4)")
    private String codigoPosicion;
}
