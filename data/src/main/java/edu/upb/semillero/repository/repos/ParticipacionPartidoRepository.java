package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.ParticipacionPartido;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParticipacionPartidoRepository extends JpaRepository<ParticipacionPartido, Long> {
    boolean existsByPartidoIdAndJugadorUsuarioId(Long partidoId, Long usuarioId);

    Optional<ParticipacionPartido> findByPartidoIdAndJugadorId(Long partidoId, Long jugadorId);

    boolean existsByPartidoIdAndClubIdNotIn(Long partidoId, Collection<Long> clubesId);

    @Query("""
            select p from ParticipacionPartido p
            where (:jugadorId is null or p.jugador.id = :jugadorId)
              and (:partidoId is null or p.partido.id = :partidoId)
              and (:clubId is null or p.club.id = :clubId)
            """)
    Page<ParticipacionPartido> findFiltered(@Param("jugadorId") Long jugadorId,
            @Param("partidoId") Long partidoId, @Param("clubId") Long clubId, Pageable pageable);

}
