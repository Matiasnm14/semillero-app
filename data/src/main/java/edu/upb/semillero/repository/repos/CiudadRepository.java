package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.Ciudad;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CiudadRepository extends JpaRepository<Ciudad, Long> {
}
