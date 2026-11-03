package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.Fichado;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FichadoRepository extends JpaRepository<Fichado, Long> {
    boolean existsByJugadorIdAndClubIdAndFechaFinalFichajeIsNull(Long jugadorId, Long clubId);

    List<Fichado> findByClubIdInAndFechaFinalFichajeIsNull(Collection<Long> clubIds);

    /** Vigencia inclusiva por día, igual que la consulta de ficha técnica. */
    @Query("""
            select distinct f.jugador.id from Fichado f
            where f.club.id in :clubIds
              and (f.fechaFichaje is null or f.fechaFichaje <= :finDelDia)
              and (f.fechaFinalFichaje is null or f.fechaFinalFichaje >= :inicioDelDia)
            """)
    Set<Long> findJugadorIdsVigentesEnClubes(@Param("clubIds") Collection<Long> clubIds,
            @Param("inicioDelDia") LocalDateTime inicioDelDia,
            @Param("finDelDia") LocalDateTime finDelDia);

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

    List<Fichado> findByJugadorId(Long jugadorId);

    List<Fichado> findByClubId(Long clubId);
}
