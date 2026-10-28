package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.UsuarioRol;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRolRepository extends JpaRepository<UsuarioRol, Long> {
    List<UsuarioRol> findByUsuarioId(Long usuarioId);

    boolean existsByUsuarioIdAndRolCodigo(Long usuarioId, String codigoRol);
}
