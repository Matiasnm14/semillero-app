package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.entity.Partido;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * La regla de choques de la agenda, en un solo lugar: la usan el generador y el
 * ajuste del fixture (tareas S2-16 y S2-17) y la reprogramación (tarea S3-17),
 * así un partido se valida igual lo mueva quien lo mueva.
 *
 * <ul>
 *   <li>Una cancha no tiene dos partidos que se crucen entre
 *       {@code fecha_inicio} y {@code fecha_fin}.</li>
 *   <li>Un equipo no juega dos partidos a la vez.</li>
 *   <li>Entre dos partidos del mismo equipo pasa el descanso mínimo del
 *       reglamento.</li>
 * </ul>
 */
final class ChoquesDeAgenda {
    /** Si un partido no tiene fecha de fin, se toma una duración de 90 minutos. */
    private static final int DURACION_SUPUESTA_MIN = 90;

    private ChoquesDeAgenda() {
    }

    /** True si el partido entra en la agenda sin chocar con ninguno de los ocupados. */
    static boolean sinChoques(Partido partido, List<Partido> ocupados, int descansoMin) {
        for (Partido otro : ocupados) {
            if (partido.getId() != null && Objects.equals(otro.getId(), partido.getId())) {
                continue;
            }
            if (mismaCancha(partido, otro) && seCruzan(partido, otro)) {
                return false;
            }
            if (compartenEquipo(partido, otro)
                    && (seCruzan(partido, otro) || sinDescanso(partido, otro, descansoMin))) {
                return false;
            }
        }
        return true;
    }

    /**
     * El motivo exacto del primer choque, o {@code null} si no hay ninguno. Lo
     * usa la reprogramación, que tiene que decir con qué partido choca.
     */
    static String primerChoque(Partido partido, List<Partido> ocupados, int descansoMin) {
        for (Partido otro : ocupados) {
            if (partido.getId() != null && Objects.equals(otro.getId(), partido.getId())) {
                continue;
            }
            if (mismaCancha(partido, otro) && seCruzan(partido, otro)) {
                return "la cancha " + partido.getCancha().getNombre() + " ya tiene el partido "
                        + otro.getId() + " a esa hora";
            }
            if (compartenEquipo(partido, otro) && seCruzan(partido, otro)) {
                return "uno de los equipos juega el partido " + otro.getId() + " a esa hora";
            }
            if (compartenEquipo(partido, otro) && sinDescanso(partido, otro, descansoMin)) {
                return "uno de los equipos juega el partido " + otro.getId()
                        + " y no quedan los " + descansoMin
                        + " minutos de descanso mínimo del reglamento";
            }
        }
        return null;
    }

    static LocalDateTime finDe(Partido partido) {
        return partido.getFechaFin() == null
                ? partido.getFechaInicio().plusMinutes(DURACION_SUPUESTA_MIN)
                : partido.getFechaFin();
    }

    private static boolean mismaCancha(Partido uno, Partido otro) {
        return uno.getCancha() != null && otro.getCancha() != null
                && Objects.equals(uno.getCancha().getId(), otro.getCancha().getId());
    }

    private static boolean compartenEquipo(Partido uno, Partido otro) {
        List<Long> clubes = List.of(otro.getClubLocal().getId(), otro.getClubVisitante().getId());
        return clubes.contains(uno.getClubLocal().getId())
                || clubes.contains(uno.getClubVisitante().getId());
    }

    private static boolean seCruzan(Partido uno, Partido otro) {
        return uno.getFechaInicio().isBefore(finDe(otro)) && otro.getFechaInicio().isBefore(finDe(uno));
    }

    /** Minutos entre el final de uno y el comienzo del otro, en el orden que vengan. */
    private static boolean sinDescanso(Partido uno, Partido otro, int descansoMin) {
        long minutos = uno.getFechaInicio().isAfter(otro.getFechaInicio())
                ? Duration.between(finDe(otro), uno.getFechaInicio()).toMinutes()
                : Duration.between(finDe(uno), otro.getFechaInicio()).toMinutes();
        return minutos < descansoMin;
    }
}
