package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.Conversacion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversacionRepository extends JpaRepository<Conversacion, Long> {
}
