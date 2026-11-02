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
 * <p>El bean se registra solo si no hay otro de su tipo. Desde que existe
 * {@code service.fichatecnica.FichaTecnicaServiceImpl} (un {@code @Service}
 * real que además lee {@code documentos_jugador}), este stub <strong>ya no se
 * activa</strong>; queda como respaldo y esta clase se puede borrar.
 *
 * <p>Diferencia con la implementación real, por si alguien vuelve a activar
 * este stub: acá {@code documentosVigentes} y {@code aptoMedicoVigente}
 * responden siempre {@code true} (no verifican nada), mientras que
 * {@code FichaTecnicaServiceImpl} los valida de verdad contra
 * {@code documentos_jugador}.
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
