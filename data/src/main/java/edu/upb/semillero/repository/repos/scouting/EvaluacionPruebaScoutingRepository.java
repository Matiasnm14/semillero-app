package edu.upb.semillero.repository.repos.scouting;

import edu.upb.semillero.repository.entity.scouting.EvaluacionPruebaScouting;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EvaluacionPruebaScoutingRepository
        extends JpaRepository<EvaluacionPruebaScouting, UUID> {
    Page<EvaluacionPruebaScouting> findByInscripcionId(UUID id, Pageable pagina);
}
