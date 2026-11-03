package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.entity.Auditoria;
import edu.upb.semillero.repository.entity.Club;
import edu.upb.semillero.repository.entity.Partido;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.entity.torneos.EventoPartido;
import edu.upb.semillero.repository.entity.torneos.InscripcionEquipo;
import edu.upb.semillero.repository.entity.torneos.ListaBuenaFe;
import edu.upb.semillero.repository.entity.torneos.Reglamento;
import edu.upb.semillero.repository.entity.torneos.Sancion;
import edu.upb.semillero.repository.entity.torneos.Torneo;
import edu.upb.semillero.repository.enums.EstadoPartido;
import edu.upb.semillero.repository.enums.torneos.EstadoSancion;
import edu.upb.semillero.repository.enums.torneos.OrigenSancion;
import edu.upb.semillero.repository.enums.torneos.TipoEventoPartido;
import edu.upb.semillero.repository.repos.AuditoriaRepository;
import edu.upb.semillero.repository.repos.torneos.EventoPartidoRepository;
import edu.upb.semillero.repository.repos.torneos.InscripcionEquipoRepository;
import edu.upb.semillero.repository.repos.torneos.ListaBuenaFeRepository;
import edu.upb.semillero.repository.repos.torneos.ReglamentoRepository;
import edu.upb.semillero.repository.repos.torneos.SancionRepository;
import edu.upb.semillero.service.EntityRelations;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * HU 13 - Lo que el cierre de un partido le hace al régimen disciplinario
 * (tareas S4-01, S4-02 y S4-03). Corre dentro de la transacción del cierre
 * (regla 7), así que si algo falla no queda el partido cerrado sin sus
 * sanciones.
 *
 * <ul>
 *   <li><strong>S4-03</strong>, primero: a cada sanción VIGENTE de los
 *       jugadores de los dos equipos le descuenta la fecha, si el partido es
 *       posterior al que la originó. Al llegar a las fechas de la suspensión
 *       queda CUMPLIDA. El W.O. cuenta como fecha, porque el equipo la
 *       pasó.</li>
 *   <li><strong>S4-02</strong>: cada roja del partido genera una suspensión
 *       de {@code fechas_suspension_roja} fechas.</li>
 *   <li><strong>S4-01</strong>: las amarillas se acumulan por jugador y
 *       torneo; cada vez que llegan a un múltiplo de {@code umbral_amarillas}
 *       se genera una suspensión de {@code fechas_suspension_amarillas}
 *       fechas.</li>
 * </ul>
 *
 * <p>Un partido se puede reabrir y volver a cerrar (tarea S3-10), así que todo
 * esto es repetible: el descuento queda en {@code auditoria} con el partido que
 * lo hizo y se revierte al reabrir, y al volver a cerrar las sanciones se
 * concilian con los eventos que quedaron, anulando las que ya no corresponden.
 */
@Component
public class SancionesAutomaticas {
    private static final String MODULO = "TORNEOS";
    private static final String TABLA = "sanciones";
    private static final String DESCONTAR = "DESCONTAR_FECHA";
    private static final String REVERTIR = "REVERTIR_DESCUENTO";
    /** Los valores por defecto de las columnas del reglamento, si el torneo no tiene. */
    private static final short UMBRAL_AMARILLAS = 3;
    private static final short FECHAS_POR_AMARILLAS = 1;
    private static final short FECHAS_POR_ROJA = 1;

    private final SancionRepository sanciones;
    private final ListaBuenaFeRepository listas;
    private final InscripcionEquipoRepository inscripciones;
    private final EventoPartidoRepository eventos;
    private final ReglamentoRepository reglamentos;
    private final AuditoriaRepository auditoria;
    private final AvisosTorneos avisos;
    private final EntityRelations relations;

    public SancionesAutomaticas(SancionRepository sanciones, ListaBuenaFeRepository listas,
            InscripcionEquipoRepository inscripciones, EventoPartidoRepository eventos,
            ReglamentoRepository reglamentos, AuditoriaRepository auditoria, AvisosTorneos avisos,
            EntityRelations relations) {
        this.sanciones = sanciones;
        this.listas = listas;
        this.inscripciones = inscripciones;
        this.eventos = eventos;
        this.reglamentos = reglamentos;
        this.auditoria = auditoria;
        this.avisos = avisos;
        this.relations = relations;
    }

    /** Lo llama el cierre con el partido ya FINALIZADO y guardado. */
    public void alCerrar(Partido partido, Long usuarioId) {
        descontarFechas(partido, usuarioId);
        if (partido.getGanadorWalkover() == null) {
            Reglamento reglamento = reglamento(partido).orElse(null);
            conciliarRojas(partido, reglamento, usuarioId);
            conciliarAcumulacion(partido, reglamento, usuarioId);
        }
    }

    /** Lo llama la reapertura: devuelve las fechas que había descontado este partido. */
    public void alReabrir(Partido partido, Long usuarioId) {
        for (Sancion sancion : delosEquipos(partido, List.of(EstadoSancion.VIGENTE, EstadoSancion.CUMPLIDA))) {
            if (descontadasPor(sancion, partido) <= 0) {
                continue;
            }
            String anterior = snapshot(sancion);
            sancion.setFechasCumplidas((short) (sancion.getFechasCumplidas() - 1));
            sancion.setEstado(EstadoSancion.VIGENTE);
            sanciones.saveAndFlush(sancion);
            registrar(REVERTIR, sancion, anterior, marca(partido), usuarioId);
        }
    }

    /** Tarea S4-03: la fecha que este partido le cuenta a cada suspendido de los dos equipos. */
    private void descontarFechas(Partido partido, Long usuarioId) {
        for (Sancion sancion : delosEquipos(partido, List.of(EstadoSancion.VIGENTE))) {
            if (!cuentaComoFecha(sancion, partido) || descontadasPor(sancion, partido) > 0) {
                continue;
            }
            String anterior = snapshot(sancion);
            sancion.setFechasCumplidas((short) (sancion.getFechasCumplidas() + 1));
            if (sancion.getFechasCumplidas() >= sancion.getFechasSuspension()) {
                sancion.setEstado(EstadoSancion.CUMPLIDA);
            }
            sanciones.saveAndFlush(sancion);
            registrar(DESCONTAR, sancion, anterior, marca(partido), usuarioId);
        }
    }

    /**
     * Un partido descuenta la fecha si es posterior a lo que originó la
     * sanción: al partido de la roja, o al alta de la sanción manual.
     */
    private boolean cuentaComoFecha(Sancion sancion, Partido partido) {
        Partido origen = sancion.getPartido();
        if (origen != null && Objects.equals(origen.getId(), partido.getId())) {
            return false;
        }
        LocalDateTime desde = origen != null ? origen.getFechaInicio() : sancion.getFechaRegistro();
        return partido.getFechaInicio().isAfter(desde);
    }

    /** Cuántas fechas le descontó este partido a la sanción, netas de las que se revirtieron. */
    private long descontadasPor(Sancion sancion, Partido partido) {
        String marca = marca(partido);
        long neto = 0;
        for (Auditoria fila : auditoria.findByTablaAndRegistroIdOrderByFechaDescIdDesc(TABLA, sancion.getId())) {
            if (!marca.equals(fila.getMotivo())) {
                continue;
            }
            if (DESCONTAR.equals(fila.getAccion())) {
                neto++;
            } else if (REVERTIR.equals(fila.getAccion())) {
                neto--;
            }
        }
        return neto;
    }

    /** Tarea S4-02: una suspensión por cada roja del partido, ni más ni menos. */
    private void conciliarRojas(Partido partido, Reglamento reglamento, Long usuarioId) {
        Torneo torneo = partido.getTorneoCategoria().getTorneo();
        Map<Long, Roja> rojas = new LinkedHashMap<>();
        for (EventoPartido evento : eventos.findByPartidoIdOrderByMinutoAscIdAsc(partido.getId())) {
            if (evento.getTipo() == TipoEventoPartido.ROJA) {
                enLista(torneo, evento.getParticipacion().getJugador().getId())
                        .ifPresent(lista -> rojas.putIfAbsent(lista.getId(), new Roja(lista, evento)));
            }
        }
        List<Sancion> existentes = generadasPor(partido, OrigenSancion.ROJA);
        for (Sancion existente : existentes) {
            if (!rojas.containsKey(existente.getListaBuenaFe().getId())) {
                anular(existente, "La roja se quitó al corregir el partido " + partido.getId(), usuarioId);
            }
        }
        Set<Long> yaSancionados = new LinkedHashSet<>();
        existentes.forEach(s -> yaSancionados.add(s.getListaBuenaFe().getId()));
        short fechas = reglamento == null ? FECHAS_POR_ROJA : reglamento.getFechasSuspensionRoja();
        rojas.forEach((listaId, roja) -> {
            if (!yaSancionados.contains(listaId)) {
                EventoPartido evento = roja.evento();
                crear(roja.lista(), partido, OrigenSancion.ROJA, fechas,
                        "Roja en el partido " + partido.getId() + ", minuto " + evento.getMinuto()
                                + (evento.getMotivo() == null ? "" : ": " + evento.getMotivo()));
            }
        });
    }

    /** La roja de un jugador y su lugar en la lista de buena fe. */
    private record Roja(ListaBuenaFe lista, EventoPartido evento) {}

    /**
     * Tarea S4-01: por cada jugador con amarillas en el partido, cuántas
     * suspensiones por acumulación le corresponden con todas las amarillas del
     * torneo, contra cuántas tiene. Faltan: se crean; sobran (porque se corrigió
     * el partido): se anulan las más recientes.
     */
    private void conciliarAcumulacion(Partido partido, Reglamento reglamento, Long usuarioId) {
        Torneo torneo = partido.getTorneoCategoria().getTorneo();
        short umbral = reglamento == null ? UMBRAL_AMARILLAS : reglamento.getUmbralAmarillas();
        short fechas = reglamento == null ? FECHAS_POR_AMARILLAS : reglamento.getFechasSuspensionAmarillas();
        if (umbral <= 0) {
            return;
        }
        Set<Long> jugadores = new LinkedHashSet<>();
        for (EventoPartido evento : eventos.findByPartidoIdOrderByMinutoAscIdAsc(partido.getId())) {
            if (evento.getTipo() == TipoEventoPartido.AMARILLA) {
                jugadores.add(evento.getParticipacion().getJugador().getId());
            }
        }
        generadasPor(partido, OrigenSancion.ACUMULACION_AMARILLAS)
                .forEach(s -> jugadores.add(s.getListaBuenaFe().getJugador().getId()));

        for (Long jugadorId : jugadores) {
            Optional<ListaBuenaFe> lista = enLista(torneo, jugadorId);
            if (lista.isEmpty()) {
                continue;
            }
            long amarillas = eventos.countByTipoAndParticipacionJugadorIdAndPartidoTorneoCategoriaTorneoIdAndPartidoEstado(
                    TipoEventoPartido.AMARILLA, jugadorId, torneo.getId(), EstadoPartido.FINALIZADO);
            long corresponden = amarillas / umbral;
            List<Sancion> tiene = new ArrayList<>(sanciones
                    .findByListaBuenaFeIdAndOrigenOrderByIdAsc(lista.get().getId(),
                            OrigenSancion.ACUMULACION_AMARILLAS).stream()
                    .filter(s -> s.getEstado() != EstadoSancion.ANULADA)
                    .toList());
            for (long i = tiene.size(); i < corresponden; i++) {
                crear(lista.get(), partido, OrigenSancion.ACUMULACION_AMARILLAS, fechas,
                        "Llegó a " + ((i + 1) * umbral) + " amarillas en el torneo (umbral "
                                + umbral + ")");
            }
            tiene.sort(Comparator.comparing(Sancion::getId).reversed());
            for (int i = 0; i < tiene.size() - corresponden; i++) {
                anular(tiene.get(i), "Ya no llega al umbral de amarillas al corregir el partido "
                        + partido.getId(), usuarioId);
            }
        }
    }

    private void crear(ListaBuenaFe lista, Partido partido, OrigenSancion origen, short fechas,
            String motivo) {
        Sancion sancion = new Sancion();
        sancion.setListaBuenaFe(lista);
        sancion.setPartido(partido);
        sancion.setOrigen(origen);
        sancion.setFechasSuspension(fechas);
        sancion.setFechasCumplidas((short) 0);
        sancion.setMotivo(recortar(motivo));
        sancion.setEstado(EstadoSancion.VIGENTE);
        sancion.setFechaRegistro(LocalDateTime.now());
        Sancion guardada = sanciones.saveAndFlush(sancion);
        registrar("SANCION_AUTOMATICA", guardada, null, guardada.getMotivo(), null);
        avisos.avisarAlJugador(lista.getJugador(), "Suspensión",
                "Tenés " + fechas + " fecha(s) de suspensión en el torneo "
                        + lista.getTorneo().getNombre() + ". " + guardada.getMotivo());
    }

    private void anular(Sancion sancion, String motivo, Long usuarioId) {
        String anterior = snapshot(sancion);
        sancion.setEstado(EstadoSancion.ANULADA);
        sanciones.saveAndFlush(sancion);
        registrar("ANULAR", sancion, anterior, recortar(motivo), usuarioId);
    }

    /** Las sanciones de los jugadores de los dos equipos del partido, en estos estados. */
    private List<Sancion> delosEquipos(Partido partido, List<EstadoSancion> estados) {
        List<Sancion> encontradas = new ArrayList<>();
        for (Club club : List.of(partido.getClubLocal(), partido.getClubVisitante())) {
            inscripciones.findByTorneoCategoriaIdAndClubId(partido.getTorneoCategoria().getId(), club.getId())
                    .map(InscripcionEquipo::getId)
                    .ifPresent(id -> encontradas.addAll(
                            sanciones.findByListaBuenaFeInscripcionEquipoIdAndEstadoIn(id, estados)));
        }
        return encontradas;
    }

    private List<Sancion> generadasPor(Partido partido, OrigenSancion origen) {
        return sanciones.findByPartidoIdOrderByIdAsc(partido.getId()).stream()
                .filter(s -> s.getOrigen() == origen)
                .filter(s -> s.getEstado() != EstadoSancion.ANULADA)
                .toList();
    }

    private Optional<ListaBuenaFe> enLista(Torneo torneo, Long jugadorId) {
        return listas.findByTorneoIdAndJugadorId(torneo.getId(), jugadorId);
    }

    private Optional<Reglamento> reglamento(Partido partido) {
        return reglamentos.findByTorneoId(partido.getTorneoCategoria().getTorneo().getId());
    }

    /** Lo que identifica en auditoría el descuento que hizo un partido. */
    private String marca(Partido partido) {
        return "Fecha cumplida en el partido " + partido.getId();
    }

    static String snapshot(Sancion sancion) {
        return "{\"estado\":\"%s\",\"fechasSuspension\":%d,\"fechasCumplidas\":%d}"
                .formatted(sancion.getEstado(), sancion.getFechasSuspension(),
                        sancion.getFechasCumplidas());
    }

    private void registrar(String accion, Sancion sancion, String anterior, String motivo,
            Long usuarioId) {
        Auditoria fila = new Auditoria();
        fila.setModulo(MODULO);
        fila.setTabla(TABLA);
        fila.setRegistroId(sancion.getId());
        fila.setAccion(accion);
        fila.setValorAnterior(anterior);
        fila.setValorNuevo(snapshot(sancion));
        fila.setMotivo(motivo);
        fila.setUsuario(relations.find(Usuario.class, usuarioId));
        fila.setFecha(LocalDateTime.now());
        auditoria.saveAndFlush(fila);
    }

    private String recortar(String texto) {
        return texto.length() <= 255 ? texto : texto.substring(0, 252) + "...";
    }
}
