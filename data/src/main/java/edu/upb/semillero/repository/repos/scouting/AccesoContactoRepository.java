package edu.upb.semillero.repository.repos.scouting;

import edu.upb.semillero.repository.entity.scouting.AccesoContacto;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AccesoContactoRepository extends JpaRepository<AccesoContacto, UUID> {}
