package edu.upb.semillero.repository.repos;

import edu.upb.semillero.repository.entity.Prueba;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PruebaRepository extends JpaRepository<Prueba, Long> {
    Page<Prueba> findByClubId(Long clubId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Prueba p where p.id = :id")
    Optional<Prueba> findByIdForUpdate(@Param("id") Long id);
}
