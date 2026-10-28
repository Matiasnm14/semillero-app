package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.Video;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoRepository extends JpaRepository<Video, Long> {
    Page<Video> findByJugadorId(Long jugadorId, Pageable pageable);
    Page<Video> findByJugadorIdAndVisibleTrue(Long jugadorId, Pageable pageable);
}
