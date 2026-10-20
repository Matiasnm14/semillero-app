package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
}
