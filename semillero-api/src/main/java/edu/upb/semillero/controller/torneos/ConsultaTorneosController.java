package edu.upb.semillero.controller.torneos;

import edu.upb.semillero.repository.dto.response.torneos.MiCuentaResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.NotificacionResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.PartidoCerradoResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.PartidoFixtureResponseDto;
import edu.upb.semillero.service.seguridad.AccesoTorneos;
import edu.upb.semillero.service.torneos.ConsultaAppService;
import edu.upb.semillero.service.torneos.FixtureService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU 14 y HU 15 - Lo que consultan la app móvil, la web y el microservicio de
 * video (tareas S4-07, S4-14, S4-15 y S4-16). La tabla, las estadísticas y las
 * sanciones tienen sus propios controladores.
 *
 * <p>"Mi cuenta" y las notificaciones son del dueño del token: con token,
 * {@code usuarioId} se puede omitir, y pedir los de otro da 403 (tarea S4-10).
 */
@Tag(name = "Torneos - HU 14 y 15", description = "Consulta para la app, la web y el microservicio de video")
@RestController
public class ConsultaTorneosController {
    private static final String TORNEOS = "/api/v1/torneos";

    private final ConsultaAppService service;
    private final FixtureService fixture;
    private final AccesoTorneos acceso;

    public ConsultaTorneosController(ConsultaAppService service, FixtureService fixture,
            AccesoTorneos acceso) {
        this.service = service;
        this.fixture = fixture;
        this.acceso = acceso;
    }

    /** HU 15: un partido de torneo, con la cancha y su superficie. */
    @GetMapping(TORNEOS + "/partidos/{partidoId}")
    public PartidoFixtureResponseDto partido(@PathVariable @Positive Long partidoId) {
        return fixture.findPartido(partidoId);
    }

    /**
     * HU 15 y punto 13 del acuerdo: los partidos cerrados después de
     * {@code desde}, para el microservicio de video si no le llegó el aviso.
     */
    @GetMapping(TORNEOS + "/partidos/finalizados")
    public List<PartidoCerradoResponseDto> finalizados(@RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde) {
        return service.cerradosDesde(desde);
    }

    /** Tarea S4-14: los próximos partidos de un jugador, con fecha, hora, cancha y superficie. */
    @GetMapping(TORNEOS + "/jugadores/{jugadorId}/proximos-partidos")
    public List<PartidoFixtureResponseDto> proximosDeJugador(@PathVariable @Positive Long jugadorId) {
        return service.proximosDeJugador(jugadorId);
    }

    /** Tarea S4-14: los próximos partidos de una categoría. */
    @GetMapping(TORNEOS + "/{torneoId}/categorias/{categoriaId}/proximos-partidos")
    public List<PartidoFixtureResponseDto> proximosDeCategoria(@PathVariable @Positive Long torneoId,
            @PathVariable @Positive Long categoriaId) {
        return service.proximosDeCategoria(torneoId, categoriaId);
    }

    /** Tarea S4-15: lo que la app muestra al entrar, con el aviso de suspensión. */
    @GetMapping(TORNEOS + "/mi-cuenta")
    public MiCuentaResponseDto miCuenta(@RequestParam(required = false) @Positive Long usuarioId) {
        return service.miCuenta(acceso.usuarioDeLaConsulta(usuarioId, TORNEOS + "/mi-cuenta"));
    }

    /** Tarea S4-16: los avisos del usuario. */
    @GetMapping(TORNEOS + "/notificaciones")
    public List<NotificacionResponseDto> notificaciones(
            @RequestParam(required = false) @Positive Long usuarioId,
            @RequestParam(defaultValue = "false") boolean soloNoLeidas) {
        return service.notificaciones(acceso.usuarioDeLaConsulta(usuarioId, TORNEOS + "/notificaciones"),
                soloNoLeidas);
    }

    @PutMapping(TORNEOS + "/notificaciones/{id}/leida")
    public NotificacionResponseDto marcarLeida(@PathVariable @Positive Long id,
            @RequestParam(required = false) @Positive Long usuarioId) {
        return service.marcarLeida(id, acceso.usuarioDeLaConsulta(usuarioId,
                TORNEOS + "/notificaciones/" + id + "/leida"));
    }

    @PutMapping(TORNEOS + "/notificaciones/leidas")
    public Map<String, Integer> marcarTodasLeidas(
            @RequestParam(required = false) @Positive Long usuarioId) {
        return Map.of("marcadas", service.marcarTodasLeidas(acceso.usuarioDeLaConsulta(usuarioId,
                TORNEOS + "/notificaciones/leidas")));
    }
}
