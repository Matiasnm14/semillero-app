package edu.upb.semillero.repository.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "clinica_de_pago")
public class ClinicaPago extends BaseEntity {

    @Column(name = "fecha_inicio_clinica")
    private LocalDateTime fechaInicioClinica;

    @Column(name = "fecha_fin_clinica")
    private LocalDateTime fechaFinClinica;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clubes_id")
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuarios_club_id")
    private UsuarioClub usuarioClub;
}
