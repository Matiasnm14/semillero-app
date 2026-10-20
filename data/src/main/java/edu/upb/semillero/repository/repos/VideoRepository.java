package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.Video;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoRepository extends JpaRepository<Video, Long> {
}
