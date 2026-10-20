package edu.upb.semillero.repository.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "jugadores")
public class Jugador extends BaseEntity {

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "pierna_habil", length = 1, columnDefinition = "char(1)")
    private String piernaHabil;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Column(name = "altura")
    private Double altura;

    @Column(name = "peso")
    private Double peso;

    @Column(name = "descripcion_jugador", length = 255)
    private String descripcionJugador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id")
    private Usuario usuario;
}
