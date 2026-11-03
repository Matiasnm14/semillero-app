package edu.upb.semillero.repository.repos.scouting;

import edu.upb.semillero.repository.entity.scouting.CuotaContacto;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CuotaContactoRepository extends JpaRepository<CuotaContacto, String> {
    @Modifying
    @Query(value = "INSERT INTO scouting_cuota(id,fecha,usadas) VALUES(:id,:fecha,0) ON CONFLICT DO NOTHING", nativeQuery = true)
    int inicializar(@Param("id") String id, @Param("fecha") LocalDate fecha);

    @Modifying
    @Query(value = "UPDATE scouting_cuota SET usadas=usadas+1 WHERE id=:id AND usadas<:limite", nativeQuery = true)
    int reservar(@Param("id") String id, @Param("limite") int limite);
}
