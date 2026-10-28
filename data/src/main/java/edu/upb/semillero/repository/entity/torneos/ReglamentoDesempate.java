package edu.upb.semillero.repository.entity.torneos;

import edu.upb.semillero.repository.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "reglamento_desempates", uniqueConstraints = {
        @UniqueConstraint(name = "uk_reglamento_desempate_orden", columnNames = {"reglamentos_id", "orden"}),
        @UniqueConstraint(name = "uk_reglamento_desempate_criterio", columnNames = {"reglamentos_id", "criterios_desempate_id"})
}, check = @CheckConstraint(name = "chk_reglamento_desempate_orden", constraint = "orden > 0"))
public class ReglamentoDesempate extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reglamentos_id", nullable = false)
    private Reglamento reglamento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criterios_desempate_id", nullable = false)
    private CriterioDesempate criterioDesempate;

    @Column(name = "orden", nullable = false)
    private Short orden;
}
