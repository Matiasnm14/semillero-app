package edu.upb.semillero.repository.repos.scouting;

import edu.upb.semillero.repository.entity.scouting.SolicitudContacto;
import edu.upb.semillero.repository.enums.scouting.EstadoContacto;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface SolicitudContactoRepository extends JpaRepository<SolicitudContacto, UUID> {
    Optional<SolicitudContacto> findBySolicitanteIdAndClave(Long solicitanteId, UUID clave);
    boolean existsByClubIdAndJugadorIdAndEstado(Long clubId, Long jugadorId, EstadoContacto estado);
    boolean existsByClubIdAndJugadorIdAndCreadaEnAfter(Long clubId, Long jugadorId, Instant desde);
    long countByClubIdAndCreadaEnAfter(Long clubId, Instant desde);
    @Query("select s from SolicitudContacto s where (s.clubId in :clubes or s.jugadorId in :jugadores) and (:estado is null or s.estado = :estado)")
    Page<SolicitudContacto> bandeja(Set<Long> clubes, Set<Long> jugadores, EstadoContacto estado, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SolicitudContacto s where s.id = :id")
    Optional<SolicitudContacto> bloquear(UUID id);
}
