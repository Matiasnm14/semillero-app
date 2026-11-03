package edu.upb.semillero.repository.entity.scouting;

import edu.upb.semillero.repository.entity.Prueba;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "scouting_convocatoria")
@Getter
@Setter
public class ConvocatoriaPrueba {
    @Id private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id")
    private Prueba prueba;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(nullable = false, length = 240)
    private String lugar;

    @Column(nullable = false, length = 80)
    private String categoria;

    @Column(nullable = false, length = 2000)
    private String descripcion;

    @Column(nullable = false)
    private int cupos;

    @Column(nullable = false)
    private Instant limiteInscripcion;

    @Column(nullable = false, length = 20)
    private String estado = "ABIERTA";

    private Integer edadMin;
    private Integer edadMax;

    @Column(nullable = false)
    private Long creadorId;

    @Column(nullable = false)
    private Instant creadaEn;

    @Version private Long version;

    @ElementCollection
    @CollectionTable(
            name = "scouting_convocatoria_posicion",
            joinColumns = @JoinColumn(name = "convocatoria_id"))
    @Column(name = "posicion", nullable = false, length = 80)
    private Set<String> posiciones = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(
            name = "scouting_convocatoria_requisito",
            joinColumns = @JoinColumn(name = "convocatoria_id"))
    @Column(name = "requisito", nullable = false, length = 240)
    private Set<String> requisitos = new LinkedHashSet<>();
}
