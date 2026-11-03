package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.Fichado;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface FichadoRepository extends JpaRepository<Fichado, Long> {
    boolean existsByJugadorIdAndClubIdAndFechaFinalFichajeIsNull(Long jugadorId, Long clubId);

    Optional<Fichado> findFirstByJugadorIdAndFechaFinalFichajeIsNullOrderByFechaFichajeDesc(Long jugadorId);

    @Query("select f from Fichado f join fetch f.club c left join fetch c.ciudad "
            + "where f.jugador.id = :jugadorId and (f.fechaFinalFichaje is null or f.fechaFinalFichaje >= :ahora) "
            + "and (f.fechaFichaje is null or f.fechaFichaje <= :ahora) order by f.fechaFichaje desc")
    Optional<Fichado> findVigenteByJugadorId(@Param("jugadorId") Long jugadorId,
            @Param("ahora") LocalDateTime ahora);

    @Query("select f from Fichado f join fetch f.club c left join fetch c.ciudad "
            + "where f.jugador.id in :jugadorIds and (f.fechaFinalFichaje is null or f.fechaFinalFichaje >= :ahora) "
            + "and (f.fechaFichaje is null or f.fechaFichaje <= :ahora) order by f.fechaFichaje desc")
    List<Fichado> findVigentesByJugadorIdIn(@Param("jugadorIds") Collection<Long> jugadorIds,
            @Param("ahora") LocalDateTime ahora);
}
