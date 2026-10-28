package edu.upb.semillero.repository.entity.torneos;

import edu.upb.semillero.repository.entity.BaseEntity;
import edu.upb.semillero.repository.entity.Club;
import edu.upb.semillero.repository.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "planilla_cuerpo_tecnico")
public class PlanillaCuerpoTecnico extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "planillas_id", nullable = false)
    private Planilla planilla;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clubes_id", nullable = false)
    private Club club;

    @Column(name = "nombre_completo", length = 100, nullable = false)
    private String nombreCompleto;

    @Column(name = "cargo", length = 30, nullable = false)
    private String cargo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id")
    private Usuario usuario;
}
