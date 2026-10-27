package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.AnalysisJob;
import edu.upb.semillero.repository.enums.EstadoAnalisis;
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

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long> {
    Optional<AnalysisJob> findFirstByVideoIdOrderByIdDesc(Long videoId);

    @EntityGraph(attributePaths = {"video", "video.partido"})
    Page<AnalysisJob> findByVideoId(Long videoId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from AnalysisJob j join fetch j.video v join fetch v.partido where j.id = :id")
    Optional<AnalysisJob> findLockedById(Long id);

    @Query("""
            select j.id from AnalysisJob j
            where j.estado in :states and j.proximaEjecucion <= :now
              and (j.leaseUntil is null or j.leaseUntil <= :now)
            order by j.proximaEjecucion, j.id
            """)
    List<Long> findExecutable(Collection<EstadoAnalisis> states, Instant now, Pageable pageable);
}
