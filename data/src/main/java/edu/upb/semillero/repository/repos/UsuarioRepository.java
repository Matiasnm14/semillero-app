package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.Usuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmailIgnoreCase(String email);

    Optional<Usuario> findByNameIgnoreCase(String name);
}
