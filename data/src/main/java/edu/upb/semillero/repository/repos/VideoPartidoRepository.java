package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.VideoPartido;
import edu.upb.semillero.repository.enums.EstadoYoutube;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface VideoPartidoRepository extends JpaRepository<VideoPartido, Long> {
    @EntityGraph(attributePaths = "partido")
    Page<VideoPartido> findByPartidoId(Long partidoId, Pageable pageable);

    Optional<VideoPartido> findByIdAndPartidoId(Long id, Long partidoId);

    List<VideoPartido> findTop10ByEstadoYoutubeInAndProximoIntentoLessThanEqualOrderById(
            Collection<EstadoYoutube> estados, Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VideoPartido v join fetch v.partido where v.id = :id")
    Optional<VideoPartido> findLockedById(Long id);
}
