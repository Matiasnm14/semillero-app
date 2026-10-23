package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.entity.torneos.CriterioDesempate;
import edu.upb.semillero.repository.repos.torneos.CriterioDesempateRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Siembra los cinco criterios de desempate de la sección E de
 * docs/torneos/bd_torneos_propuesta.sql. Es idempotente: si el código ya existe
 * no hace nada, así que puede correr en cada arranque.
 *
 * <p>Cubre en parte la tarea S1-07 mientras el grupo no decida si se agrega
 * Flyway. Solo escribe en criterios_desempate, una tabla propia de torneos.
 */
@Component
public class CriteriosDesempateSeeder implements ApplicationRunner {
    private static final Map<String, String> CRITERIOS = new LinkedHashMap<>();

    static {
        CRITERIOS.put("DIF_GOLES", "Diferencia de goles");
        CRITERIOS.put("GOLES_FAVOR", "Goles a favor");
        CRITERIOS.put("ENFRENTAMIENTO", "Enfrentamiento directo");
        CRITERIOS.put("FAIR_PLAY", "Fair play");
        CRITERIOS.put("SORTEO", "Sorteo");
    }

    private final CriterioDesempateRepository repository;

    public CriteriosDesempateSeeder(CriterioDesempateRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        CRITERIOS.forEach((codigo, nombre) -> {
            if (!repository.existsByCodigo(codigo)) {
                CriterioDesempate criterio = new CriterioDesempate();
                criterio.setCodigo(codigo);
                criterio.setNombre(nombre);
                repository.save(criterio);
            }
        });
        repository.flush();
    }
}
