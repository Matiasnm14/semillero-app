package edu.upb.semillero.repository.dto.request.torneos;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * HU 7 - Generar el fixture (tareas S2-14, S2-15 y S2-16). Sirve para la liga
 * por rotación y para las llaves de eliminación directa.
 *
 * <p>Las canchas son las que el organizador pone a disposición; el generador
 * reparte los partidos entre ellas sin choques y respetando el descanso mínimo
 * del reglamento.
 *
 * <p>Lo que se puede omitir y su valor por defecto: {@code horaPrimerPartido}
 * 09:00, {@code horaUltimoPartido} 22:00 (ningún partido termina después),
 * {@code diasEntreJornadas} 7, {@code minutosEntrePartidos} 15,
 * {@code duracionPartidoMin} los dos tiempos de la categoría más 15 minutos de
 * entretiempo, {@code idaVuelta} lo que diga el torneo y {@code siembra} true
 * (en eliminación directa, ordenar por cabeza de serie en vez de sortear).
 */
public record GenerarFixtureRequestDto(
        @NotNull LocalDate fechaPrimeraJornada,
        LocalTime horaPrimerPartido,
        LocalTime horaUltimoPartido,
        @Positive Integer diasEntreJornadas,
        @Positive Integer minutosEntrePartidos,
        @Positive Integer duracionPartidoMin,
        @NotEmpty List<@Positive Long> canchaIds,
        Boolean idaVuelta,
        Boolean siembra,
        Long semillaSorteo,
        @NotNull @Positive Long usuarioId
) {}
