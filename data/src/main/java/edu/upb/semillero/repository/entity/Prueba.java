package edu.upb.semillero.repository.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "pruebas")
public class Prueba extends BaseEntity {

    @Column(name = "fecha_prueba")
    private LocalDate fechaPrueba;

    @Column(name = "hora_prueba")
    private LocalTime horaPrueba;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clubes_id")
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuarios_club_id")
    private UsuarioClub usuarioClub;
}
