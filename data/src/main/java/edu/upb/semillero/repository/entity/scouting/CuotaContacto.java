package edu.upb.semillero.repository.entity.scouting;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "scouting_cuota") @Getter @Setter
public class CuotaContacto {
    @Id @Column(length = 100) private String id;
    @Column(nullable = false) private LocalDate fecha;
    @Column(nullable = false) private int usadas;
}
