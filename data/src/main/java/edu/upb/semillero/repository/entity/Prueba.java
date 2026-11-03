package edu.upb.semillero.repository.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;
import edu.upb.semillero.repository.enums.EstadoPrueba;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "pruebas")
public class Prueba extends BaseEntity {

    @Column(name = "titulo", length = 120)
    private String titulo;

    @Column(name = "fecha_prueba")
    private LocalDate fechaPrueba;

    @Column(name = "hora_prueba")
    private LocalTime horaPrueba;

    @Column(name = "lugar", length = 240)
    private String lugar;

    @Column(name = "categoria", length = 40)
    private String categoria;

    @Column(name = "cupos")
    private Integer cupos;

    @Column(name = "descripcion", columnDefinition = "text")
    private String descripcion;

    @Column(name = "requisitos", columnDefinition = "text")
    private String requisitos;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 20, nullable = false)
    private EstadoPrueba estado = EstadoPrueba.ABIERTA;

    @ManyToMany
    @JoinTable(name = "prueba_posiciones",
            joinColumns = @JoinColumn(name = "prueba_id"),
            inverseJoinColumns = @JoinColumn(name = "posiciones_id"))
    private Set<Posicion> posiciones = new LinkedHashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clubes_id")
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuarios_club_id")
    private UsuarioClub usuarioClub;
}
