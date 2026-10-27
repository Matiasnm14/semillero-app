package edu.upb.semillero.repository.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class Usuario extends BaseEntity {

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "telefono_usuario", length = 20)
    private String telefonoUsuario;

    @Column(name = "doc_identidad_usuario", length = 20)
    private String docIdentidadUsuario;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "nombres", length = 35)
    private String nombres;

    @Column(name = "apellidos", length = 35)
    private String apellidos;

    @Column(name = "foto_perfil", columnDefinition = "text")
    private String fotoPerfil;
}
