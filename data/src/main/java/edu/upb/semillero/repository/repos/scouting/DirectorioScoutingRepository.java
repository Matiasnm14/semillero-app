package edu.upb.semillero.repository.repos.scouting;

import edu.upb.semillero.repository.entity.Jugador;
import edu.upb.semillero.repository.dto.response.scouting.*;
import java.time.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Lectura sobre perfiles maestros; scouting no guarda copias de estos datos. */
public interface DirectorioScoutingRepository extends Repository<Jugador, Long> {
    @Query("""
        select new edu.upb.semillero.repository.dto.response.scouting.JugadorDirectorioDto(
            j.id, u.nombres, u.apellidos, j.altura, j.peso, j.piernaHabil, j.descripcionJugador)
        from Jugador j join j.usuario u, PreferenciaContacto p
        where p.jugadorId = j.id and p.visible = true and j.fechaNacimiento <= :mayoria
          and j.estado = edu.upb.semillero.repository.enums.EstadoJugador.ACTIVO
          and (:id is null or j.id = :id)
          and (lower(concat(coalesce(u.nombres,''), ' ', coalesce(u.apellidos,''))) like :texto escape '!')
          and (:posicionId is null or exists (select pj.id from PosicionJugador pj
              where pj.jugador = j and pj.posicion.id = :posicionId))
          and (:clubId is null or exists (select f.id from Fichado f where f.jugador = j
              and f.club.id = :clubId and (f.fechaFichaje is null or f.fechaFichaje <= :ahora)
              and (f.fechaFinalFichaje is null or f.fechaFinalFichaje >= :ahora)))
        """)
    Page<JugadorDirectorioDto> jugadores(Long id, String texto, Long posicionId, Long clubId,
            LocalDate mayoria, LocalDateTime ahora, Pageable pageable);

    @Query("""
        select new edu.upb.semillero.repository.dto.response.scouting.ClubDirectorioDto(
            c.id, c.nombreClub, c.ciudad.id, c.anioFundacion, c.descripcionClub)
        from Club c where lower(coalesce(c.nombreClub,'')) like :texto escape '!'
        """)
    Page<ClubDirectorioDto> clubes(String texto, Pageable pageable);
}
