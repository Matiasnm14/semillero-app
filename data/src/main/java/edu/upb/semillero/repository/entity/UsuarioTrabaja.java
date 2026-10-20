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
@Table(name = "usuario_trabaja")
public class UsuarioTrabaja extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuarios_club_id")
    private UsuarioClub usuarioClub;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tipo_usuario_club", length = 2, columnDefinition = "char(2)")
    private String tipoUsuarioClub;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clubes_id")
    private Club club;
}
