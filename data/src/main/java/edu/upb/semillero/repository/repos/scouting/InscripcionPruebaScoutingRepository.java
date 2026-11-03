package edu.upb.semillero.repository.repos.scouting;

import edu.upb.semillero.repository.entity.scouting.InscripcionPruebaScouting;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface InscripcionPruebaScoutingRepository
        extends JpaRepository<InscripcionPruebaScouting, UUID> {
    Optional<InscripcionPruebaScouting> findByConvocatoriaIdAndJugadorId(
            Long convocatoriaId, Long jugadorId);

    @Query(
            "select count(i) from InscripcionPruebaScouting i where i.convocatoria.id=:id and"
                + " i.estado in ('PENDIENTE','PRESELECCIONADO','ACEPTADO')")
    long ocupados(Long id);

    Page<InscripcionPruebaScouting> findByConvocatoriaId(Long id, Pageable pagina);
}
