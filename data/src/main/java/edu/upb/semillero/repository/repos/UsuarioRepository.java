package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.Usuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findFirstByEmailIgnoreCaseOrNameIgnoreCaseOrderByIdDesc(String email, String name);

    Optional<Usuario> findByEmailIgnoreCase(String email);

    Optional<Usuario> findByNameIgnoreCase(String name);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
