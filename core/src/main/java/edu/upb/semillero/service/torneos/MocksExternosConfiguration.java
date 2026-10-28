package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.entity.Fichado;
import edu.upb.semillero.repository.entity.Jugador;
import edu.upb.semillero.repository.repos.JugadorRepository;
import edu.upb.semillero.repository.repos.torneos.FichajeConsultaRepository;
import edu.upb.semillero.service.fichatecnica.FichaTecnicaService;
import edu.upb.semillero.service.fichatecnica.JugadorParaTorneosDto;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * La implementación de {@code FichaTecnicaService} con la que torneos trabaja
 * mientras ficha técnica no escribe la suya (punto 10 del acuerdo de la S1-04).
 *
 * <p>{@code ComunService} ya no está acá: la tarea S2-02 le dio una
 * implementación de verdad, {@code service.comun.ComunServiceImpl}, con las
 * tablas {@code roles} y {@code usuarios_roles} de la migración V008.
 *
 * <p>El bean se registra solo si no hay otro de su tipo, así que el día que
 * ficha técnica agregue su {@code @Service} real, este se apaga sin que nadie
 * toque nada, y esta clase se puede borrar.
 *
 * <p><strong>Qué es real y qué no</strong>, para que nadie lo confunda:
 *
 * <ul>
 *   <li>{@code FichaTecnicaService} <strong>lee la base</strong> (tarea S2-05):
 *       la edad sale de {@code jugadores.fecha_nacimiento}, el estado de
 *       {@code jugadores.estado} y el fichaje de la tabla {@code fichado}. Son
 *       tablas de la BD acordada y solo se leen, nunca se escriben.</li>
 *   <li>Lo único que sigue inventado ahí es {@code documentosVigentes} y
 *       {@code aptoMedicoVigente}, que responden {@code true}: la tabla
 *       {@code documentos_jugador} es de ficha técnica (punto 7 del acuerdo) y
 *       todavía no existe. Hasta que exista, esas dos validaciones de la HU 5
 *       <strong>no comprueban nada</strong>.</li>
 * </ul>
 */
@Configuration
public class MocksExternosConfiguration {

    /**
     * Edad que se devuelve cuando el jugador no tiene fecha de nacimiento
     * cargada. {@code ListaBuenaFeService} la lee como "edad desconocida" y
     * rechaza al jugador con ese motivo, en vez de dejarlo pasar sin validar.
     */
    public static final int EDAD_DESCONOCIDA = 0;

    @Bean
    @ConditionalOnMissingBean(FichaTecnicaService.class)
    FichaTecnicaService fichaTecnicaServiceLecturaDirecta(JugadorRepository jugadores,
            FichajeConsultaRepository fichajes) {
        return new FichaTecnicaLecturaDirecta(jugadores, fichajes);
    }

    /**
     * Lee de la BD acordada los hechos que torneos necesita para la lista de
     * buena fe. No es la implementación definitiva: esa la debe ficha técnica,
     * con los documentos y el apto médico de verdad.
     */
    private static class FichaTecnicaLecturaDirecta implements FichaTecnicaService {
        private static final Logger log = LoggerFactory.getLogger(FichaTecnicaLecturaDirecta.class);

        private final JugadorRepository jugadores;
        private final FichajeConsultaRepository fichajes;

        FichaTecnicaLecturaDirecta(JugadorRepository jugadores, FichajeConsultaRepository fichajes) {
            this.jugadores = jugadores;
            this.fichajes = fichajes;
        }

        @Override
        public Optional<JugadorParaTorneosDto> datosParaTorneos(Long jugadorId, LocalDate fechaCorte) {
            if (jugadorId == null) {
                return Optional.empty();
            }
            LocalDate corte = fechaCorte == null ? LocalDate.now() : fechaCorte;
            return jugadores.findById(jugadorId).map(jugador -> new JugadorParaTorneosDto(
                    jugador.getId(),
                    edadA(jugador, corte),
                    jugador.getEstado(),
                    documentosVigentes(jugador),
                    aptoMedicoVigente(jugador),
                    clubesDondeEstaFichado(jugador.getId(), corte)));
        }

        @Override
        public List<Long> jugadoresFichadosEn(Long clubId, LocalDate fecha) {
            if (clubId == null) {
                return List.of();
            }
            LocalDate dia = fecha == null ? LocalDate.now() : fecha;
            return fichajes.findByClubId(clubId).stream()
                    .filter(fichado -> vigente(fichado, dia))
                    .map(fichado -> fichado.getJugador() == null ? null : fichado.getJugador().getId())
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        }

        private int edadA(Jugador jugador, LocalDate fechaCorte) {
            LocalDate nacimiento = jugador.getFechaNacimiento();
            if (nacimiento == null || nacimiento.isAfter(fechaCorte)) {
                return EDAD_DESCONOCIDA;
            }
            return Period.between(nacimiento, fechaCorte).getYears();
        }

        /** Siempre true: {@code documentos_jugador} todavía no existe. */
        private boolean documentosVigentes(Jugador jugador) {
            log.warn("Los documentos del jugador {} no se verifican: documentos_jugador es de ficha"
                    + " técnica y todavía no existe", jugador.getId());
            return true;
        }

        /** Siempre true, por el mismo motivo que los documentos. */
        private boolean aptoMedicoVigente(Jugador jugador) {
            return true;
        }

        private Set<Long> clubesDondeEstaFichado(Long jugadorId, LocalDate fecha) {
            return fichajes.findByJugadorId(jugadorId).stream()
                    .filter(fichado -> vigente(fichado, fecha))
                    .map(fichado -> fichado.getClub() == null ? null : fichado.getClub().getId())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        }

        /**
         * Un fichaje vale a una fecha si ya empezó y todavía no terminó. Las dos
         * columnas de {@code fichado} aceptan NULL, y un NULL se toma como
         * abierto de ese lado.
         */
        private boolean vigente(Fichado fichado, LocalDate fecha) {
            LocalDateTime desde = fichado.getFechaFichaje();
            LocalDateTime hasta = fichado.getFechaFinalFichaje();
            if (desde != null && desde.isAfter(fecha.atTime(LocalTime.MAX))) {
                return false;
            }
            return hasta == null || !hasta.isBefore(fecha.atStartOfDay());
        }
    }
}
