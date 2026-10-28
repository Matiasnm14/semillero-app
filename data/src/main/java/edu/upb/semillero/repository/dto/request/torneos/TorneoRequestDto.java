package edu.upb.semillero.repository.dto.request.torneos;

import edu.upb.semillero.repository.enums.torneos.AmbitoTorneo;
import edu.upb.semillero.repository.enums.torneos.FormatoTorneo;
import edu.upb.semillero.repository.enums.torneos.TipoTorneo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * El estado no se recibe: el torneo nace en BORRADOR y solo cambia por
 * {@code POST /api/v1/torneos/{id}/abrir-inscripciones}.
 *
 * <p>{@code usuarioId} es el organizador que crea el torneo. Viaja en el cuerpo
 * porque el repositorio todavía no tiene autenticación (HU 16).
 */
public record TorneoRequestDto(
        @NotBlank @Size(max = 100) String nombre,
        @NotNull @Positive Short temporada,
        @NotNull TipoTorneo tipo,
        @NotNull AmbitoTorneo ambito,
        @Size(max = 100) String organizadorExterno,
        @NotNull FormatoTorneo formato,
        Boolean idaVuelta,
        @NotNull LocalDate fechaInicio,
        @NotNull LocalDate fechaFin,
        @NotNull LocalDate fechaLimiteInscripcion,
        @NotNull @Positive Long usuarioId,
        List<@Valid TorneoCategoriaRequestDto> categorias
) {}
