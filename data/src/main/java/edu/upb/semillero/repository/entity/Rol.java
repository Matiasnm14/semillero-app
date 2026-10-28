package edu.upb.semillero.repository.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Rol del sistema (HU 16). Compartida entre los cuatro módulos, como
 * {@code auditoria}: la crea torneos en la migración V008 porque ficha técnica
 * no llegó a hacerlo (puntos 6 y 21 del acuerdo de la S1-04).
 *
 * <p>Los códigos son ADMIN, ORGANIZADOR, ENTRENADOR, MESA_CONTROL y CONSULTA.
 * ADMIN y ENTRENADOR igual se derivan de {@code administrador} y de
 * {@code usuario_trabaja}, así que acá se usan para los otros tres y para
 * sumarle roles a alguien a mano.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "roles",
        uniqueConstraints = @UniqueConstraint(name = "uk_rol_codigo", columnNames = "codigo"))
public class Rol extends BaseEntity {

    @Column(name = "codigo", length = 20, nullable = false)
    private String codigo;

    @Column(name = "nombre", length = 60, nullable = false)
    private String nombre;
}
