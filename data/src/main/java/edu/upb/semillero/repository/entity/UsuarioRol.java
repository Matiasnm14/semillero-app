package edu.upb.semillero.repository.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

/** Los roles que tiene un usuario (HU 16). Ver {@link Rol}. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "usuarios_roles",
        uniqueConstraints = @UniqueConstraint(name = "uk_usuario_rol",
                columnNames = {"users_id", "roles_id"}),
        indexes = @Index(name = "idx_usuarios_roles_usuario", columnList = "users_id"))
public class UsuarioRol extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "users_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roles_id", nullable = false)
    private Rol rol;

    @Column(name = "fecha_alta", nullable = false)
    @ColumnDefault("now()")
    private LocalDateTime fechaAlta;
}
