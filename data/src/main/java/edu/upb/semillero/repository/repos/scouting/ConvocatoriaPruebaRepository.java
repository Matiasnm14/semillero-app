package edu.upb.semillero.repository.repos.scouting;

import edu.upb.semillero.repository.entity.scouting.ConvocatoriaPrueba;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

import java.util.Optional;

public interface ConvocatoriaPruebaRepository extends JpaRepository<ConvocatoriaPrueba, Long> {
    @Query("select c from ConvocatoriaPrueba c where (:clubId is null or c.prueba.club.id=:clubId)")
    Page<ConvocatoriaPrueba> buscar(Long clubId, Pageable pagina);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConvocatoriaPrueba c where c.id=:id")
    Optional<ConvocatoriaPrueba> bloquear(Long id);
}
