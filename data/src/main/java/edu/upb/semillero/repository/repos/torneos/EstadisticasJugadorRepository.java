package edu.upb.semillero.repository.repos.torneos;

import edu.upb.semillero.repository.entity.ParticipacionPartido;
import edu.upb.semillero.repository.enums.EstadoPartido;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Consulta de solo lectura sobre las participaciones compartidas. */
public interface EstadisticasJugadorRepository extends Repository<ParticipacionPartido, Long> {
    @EntityGraph(attributePaths = {"jugador", "partido", "club"})
    @Query("""
            select p from ParticipacionPartido p
            join p.partido partido
            left join partido.torneoCategoria categoria
            left join categoria.torneo torneo
            where p.jugador.id in :jugadores
              and partido.estado = :estado
              and partido.ganadorWalkover is null
              and (:temporada is null
                   or (torneo.id is not null and torneo.temporada = :temporada)
                   or (torneo.id is null and year(partido.fechaInicio) = :temporada))
              and (:torneoId is null or torneo.id = :torneoId)
            order by partido.fechaInicio desc, partido.id desc, p.id desc
            """)
    List<ParticipacionPartido> findParticipations(@Param("jugadores") List<Long> jugadores,
            @Param("estado") EstadoPartido estado, @Param("temporada") Integer temporada,
            @Param("torneoId") Long torneoId);
}
