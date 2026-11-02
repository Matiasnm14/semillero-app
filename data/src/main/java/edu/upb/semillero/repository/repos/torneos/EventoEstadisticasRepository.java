package edu.upb.semillero.repository.repos.torneos;

import edu.upb.semillero.repository.entity.torneos.EventoPartido;
import edu.upb.semillero.repository.enums.torneos.TipoEventoPartido;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface EventoEstadisticasRepository extends Repository<EventoPartido, Long> {
    @EntityGraph(attributePaths = {"partido", "club", "participacion", "participacion.partido",
            "participacion.jugador", "participacion.club", "participacionRelacionada",
            "participacionRelacionada.partido", "participacionRelacionada.jugador", "participacionRelacionada.club"})
    @Query("""
            select e from EventoPartido e
            left join e.participacionRelacionada relacionada
            where e.tipo = :tipo
              and (e.participacion.id in :participaciones or relacionada.id in :participaciones)
            """)
    List<EventoPartido> findGoals(@Param("participaciones") List<Long> participaciones,
            @Param("tipo") TipoEventoPartido tipo);
}
