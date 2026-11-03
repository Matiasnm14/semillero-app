package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.Jugador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import java.util.Optional;

public interface JugadorRepository extends JpaRepository<Jugador, Long>, JpaSpecificationExecutor<Jugador> {
    Optional<Jugador> findFirstByUsuarioId(Long usuarioId);

    @Override
    @EntityGraph(attributePaths = "usuario")
    Page<Jugador> findAll(Specification<Jugador> specification, Pageable pageable);
}
