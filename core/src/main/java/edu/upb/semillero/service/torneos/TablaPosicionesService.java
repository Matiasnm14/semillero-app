package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.dto.response.torneos.FilaTablaResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.TablaPosicionesResponseDto;
import edu.upb.semillero.repository.entity.Club;
import edu.upb.semillero.repository.entity.Partido;
import edu.upb.semillero.repository.entity.torneos.EventoPartido;
import edu.upb.semillero.repository.entity.torneos.Grupo;
import edu.upb.semillero.repository.entity.torneos.InscripcionEquipo;
import edu.upb.semillero.repository.entity.torneos.Reglamento;
import edu.upb.semillero.repository.entity.torneos.Torneo;
import edu.upb.semillero.repository.entity.torneos.TorneoCategoria;
import edu.upb.semillero.repository.enums.EstadoPartido;
import edu.upb.semillero.repository.enums.FaseTorneo;
import edu.upb.semillero.repository.enums.torneos.AmbitoTorneo;
import edu.upb.semillero.repository.enums.torneos.EstadoInscripcion;
import edu.upb.semillero.repository.enums.torneos.FormatoTorneo;
import edu.upb.semillero.repository.enums.torneos.TipoEventoPartido;
import edu.upb.semillero.repository.repos.torneos.EventoPartidoRepository;
import edu.upb.semillero.repository.repos.torneos.GrupoRepository;
import edu.upb.semillero.repository.repos.torneos.InscripcionEquipoRepository;
import edu.upb.semillero.repository.repos.torneos.PartidoFixtureRepository;
import edu.upb.semillero.repository.repos.torneos.ReglamentoDesempateRepository;
import edu.upb.semillero.repository.repos.torneos.ReglamentoRepository;
import edu.upb.semillero.repository.repos.torneos.TorneoCategoriaRepository;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.NotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU 11 - Tabla de posiciones (tareas S3-12, S3-13 y S3-14).
 *
 * <ul>
 *   <li><strong>S3-12</strong>: PJ, PG, PE, PP, GF, GC, DG y puntos, con los
 *       puntos por resultado del reglamento. No se guarda (regla 5): se
 *       calcula desde los partidos FINALIZADO, así que un partido recién
 *       cerrado o un reglamento cambiado se ven en la próxima consulta.</li>
 *   <li><strong>S3-13</strong>: a igualdad de puntos, los desempates en el
 *       orden de {@code reglamento_desempates}. Cada criterio parte el grupo de
 *       empatados y el siguiente solo se aplica a los que siguen empatados; así
 *       el enfrentamiento directo se calcula entre los empatados, no de a
 *       pares.</li>
 *   <li><strong>S3-14</strong>: una tabla por categoría, o una por grupo si el
 *       torneo se juega por grupos. En un campeonato externo no hay tabla, y en
 *       eliminación directa tampoco: se juega por llaves.</li>
 * </ul>
 *
 * <p>Los partidos anulados (CANCELADO) no cuentan. El W.O. cuenta como un
 * partido ganado por los goles de W.O. del reglamento, que el cierre ya dejó
 * guardados.
 */
@Service
@Transactional(readOnly = true)
public class TablaPosicionesService {
    private static final String DIF_GOLES = "DIF_GOLES";
    private static final String GOLES_FAVOR = "GOLES_FAVOR";
    private static final String ENFRENTAMIENTO = "ENFRENTAMIENTO";
    private static final String FAIR_PLAY = "FAIR_PLAY";
    private static final String SORTEO = "SORTEO";
    private static final int PUNTOS_AMARILLA = 1;
    private static final int PUNTOS_ROJA = 3;

    private final TorneoCategoriaRepository categorias;
    private final PartidoFixtureRepository partidos;
    private final InscripcionEquipoRepository inscripciones;
    private final GrupoRepository grupos;
    private final ReglamentoRepository reglamentos;
    private final ReglamentoDesempateRepository desempates;
    private final EventoPartidoRepository eventos;

    public TablaPosicionesService(TorneoCategoriaRepository categorias,
            PartidoFixtureRepository partidos, InscripcionEquipoRepository inscripciones,
            GrupoRepository grupos, ReglamentoRepository reglamentos,
            ReglamentoDesempateRepository desempates, EventoPartidoRepository eventos) {
        this.categorias = categorias;
        this.partidos = partidos;
        this.inscripciones = inscripciones;
        this.grupos = grupos;
        this.reglamentos = reglamentos;
        this.desempates = desempates;
        this.eventos = eventos;
    }

    /**
     * Las tablas de la categoría: una sola en liga, una por grupo en el formato
     * de grupos. Con {@code grupoId} devuelve solo la de ese grupo.
     */
    public List<TablaPosicionesResponseDto> tablas(Long torneoId, Long categoriaId, Long grupoId) {
        TorneoCategoria categoria = categorias.findByIdAndTorneoId(categoriaId, torneoId)
                .orElseThrow(() -> new NotFoundException("La categoría con id " + categoriaId
                        + " no existe en el torneo " + torneoId));
        Torneo torneo = categoria.getTorneo();
        if (torneo.getAmbito() == AmbitoTorneo.EXTERNO) {
            throw new BusinessException("El torneo " + torneo.getNombre()
                    + " es un campeonato externo: no tiene tabla de posiciones");
        }
        if (torneo.getFormato() == FormatoTorneo.ELIMINACION_DIRECTA) {
            throw new BusinessException("El torneo " + torneo.getNombre()
                    + " es de eliminación directa: se juega por llaves y no tiene tabla");
        }
        Reglamento reglamento = reglamentos.findByTorneoId(torneo.getId())
                .orElseThrow(() -> new BusinessException("El torneo " + torneo.getNombre()
                        + " no tiene reglamento: sin los puntos por resultado no hay tabla"));
        List<String> criterios = desempates.findByReglamentoIdOrderByOrdenAsc(reglamento.getId())
                .stream()
                .map(desempate -> desempate.getCriterioDesempate().getCodigo())
                .toList();
        List<InscripcionEquipo> aprobadas = inscripciones
                .findByTorneoCategoriaIdAndEstadoOrderByIdAsc(categoria.getId(), EstadoInscripcion.APROBADA);
        List<Partido> cerrados = partidos.findByTorneoCategoriaIdOrderByJornadaAscFechaInicioAsc(
                        categoria.getId()).stream()
                .filter(partido -> partido.getEstado() == EstadoPartido.FINALIZADO)
                .toList();

        if (torneo.getFormato() == FormatoTorneo.LIGA) {
            if (grupoId != null) {
                throw new BusinessException("El torneo " + torneo.getNombre()
                        + " es de liga: no tiene grupos");
            }
            List<Partido> deLiga = cerrados.stream()
                    .filter(partido -> partido.getFase() == FaseTorneo.LIGA).toList();
            return List.of(tabla(categoria, null, clubesDe(aprobadas), deLiga, reglamento, criterios));
        }

        List<Grupo> delaCategoria = grupos.findByTorneoCategoriaId(categoria.getId()).stream()
                .sorted(Comparator.comparing(Grupo::getNombre))
                .toList();
        if (grupoId != null) {
            delaCategoria = delaCategoria.stream()
                    .filter(grupo -> Objects.equals(grupo.getId(), grupoId))
                    .toList();
            if (delaCategoria.isEmpty()) {
                throw new NotFoundException("El grupo con id " + grupoId
                        + " no existe en la categoría " + categoria.getNombre());
            }
        }
        List<TablaPosicionesResponseDto> tablas = new ArrayList<>();
        for (Grupo grupo : delaCategoria) {
            List<Club> equipos = clubesDe(aprobadas.stream()
                    .filter(i -> i.getGrupo() != null && Objects.equals(i.getGrupo().getId(), grupo.getId()))
                    .toList());
            List<Partido> delGrupo = cerrados.stream()
                    .filter(partido -> partido.getFase() == FaseTorneo.GRUPOS)
                    .filter(partido -> partido.getGrupo() != null
                            && Objects.equals(partido.getGrupo().getId(), grupo.getId()))
                    .toList();
            tablas.add(tabla(categoria, grupo, equipos, delGrupo, reglamento, criterios));
        }
        return tablas;
    }

    private List<Club> clubesDe(List<InscripcionEquipo> inscritas) {
        return inscritas.stream().map(InscripcionEquipo::getClub).toList();
    }

    /** Tarea S3-12: acumula los partidos cerrados y ordena con los desempates. */
    private TablaPosicionesResponseDto tabla(TorneoCategoria categoria, Grupo grupo, List<Club> equipos,
            List<Partido> cerrados, Reglamento reglamento, List<String> criterios) {
        Map<Long, Fila> filas = new LinkedHashMap<>();
        equipos.forEach(club -> filas.put(club.getId(), new Fila(club)));
        for (Partido partido : cerrados) {
            Fila local = filas.computeIfAbsent(partido.getClubLocal().getId(),
                    id -> new Fila(partido.getClubLocal()));
            Fila visitante = filas.computeIfAbsent(partido.getClubVisitante().getId(),
                    id -> new Fila(partido.getClubVisitante()));
            int golesLocal = goles(partido.getGolesLocal());
            int golesVisitante = goles(partido.getGolesVisitante());
            local.sumar(golesLocal, golesVisitante, reglamento);
            visitante.sumar(golesVisitante, golesLocal, reglamento);
        }
        sumarFairPlay(filas, cerrados);

        List<Fila> ordenadas = new ArrayList<>();
        Comparator<Fila> porPuntos = Comparator.comparingInt((Fila fila) -> fila.puntos).reversed();
        for (List<Fila> empatados : bloques(new ArrayList<>(filas.values()), porPuntos)) {
            ordenadas.addAll(desempatar(empatados, criterios, 0, cerrados, categoria, reglamento));
        }
        List<FilaTablaResponseDto> respuesta = new ArrayList<>();
        for (int i = 0; i < ordenadas.size(); i++) {
            respuesta.add(ordenadas.get(i).toResponse(i + 1));
        }
        return new TablaPosicionesResponseDto(
                categoria.getId(),
                grupo == null ? null : grupo.getId(),
                grupo == null ? null : grupo.getNombre(),
                criterios,
                cerrados.size(),
                respuesta);
    }

    /**
     * Tarea S3-13: aplica el criterio {@code indice} a un grupo de empatados y,
     * a los que siguen empatados, el siguiente. Si se terminan los criterios y
     * siguen empatados, quedan en el orden del id del club, que es estable.
     */
    private List<Fila> desempatar(List<Fila> empatados, List<String> criterios, int indice,
            List<Partido> cerrados, TorneoCategoria categoria, Reglamento reglamento) {
        if (empatados.size() <= 1) {
            return empatados;
        }
        if (indice >= criterios.size()) {
            return empatados.stream().sorted(Comparator.comparing(fila -> fila.club.getId())).toList();
        }
        Comparator<Fila> criterio = criterio(criterios.get(indice), empatados, cerrados, categoria,
                reglamento);
        List<Fila> ordenadas = new ArrayList<>();
        for (List<Fila> siguenEmpatados : bloques(empatados, criterio)) {
            ordenadas.addAll(desempatar(siguenEmpatados, criterios, indice + 1, cerrados, categoria,
                    reglamento));
        }
        return ordenadas;
    }

    private Comparator<Fila> criterio(String codigo, List<Fila> empatados, List<Partido> cerrados,
            TorneoCategoria categoria, Reglamento reglamento) {
        return switch (codigo) {
            case DIF_GOLES -> Comparator.comparingInt((Fila fila) -> fila.diferencia()).reversed();
            case GOLES_FAVOR -> Comparator.comparingInt((Fila fila) -> fila.golesFavor).reversed();
            case ENFRENTAMIENTO -> enfrentamientoDirecto(empatados, cerrados, reglamento);
            case FAIR_PLAY -> Comparator.comparingInt((Fila fila) -> fila.fairPlay);
            case SORTEO -> Comparator.comparingLong((Fila fila) -> sorteo(categoria, fila.club));
            default -> (una, otra) -> 0;
        };
    }

    /**
     * Una mini tabla con los partidos que jugaron entre sí los empatados:
     * primero los puntos, con los del reglamento, y después la diferencia de
     * goles de esos partidos.
     */
    private Comparator<Fila> enfrentamientoDirecto(List<Fila> empatados, List<Partido> cerrados,
            Reglamento reglamento) {
        Set<Long> ids = empatados.stream().map(fila -> fila.club.getId()).collect(Collectors.toSet());
        Map<Long, int[]> mini = new LinkedHashMap<>();
        ids.forEach(id -> mini.put(id, new int[2]));
        for (Partido partido : cerrados) {
            Long local = partido.getClubLocal().getId();
            Long visitante = partido.getClubVisitante().getId();
            if (!ids.contains(local) || !ids.contains(visitante)) {
                continue;
            }
            int golesLocal = goles(partido.getGolesLocal());
            int golesVisitante = goles(partido.getGolesVisitante());
            mini.get(local)[0] += puntos(golesLocal, golesVisitante, reglamento);
            mini.get(visitante)[0] += puntos(golesVisitante, golesLocal, reglamento);
            mini.get(local)[1] += golesLocal - golesVisitante;
            mini.get(visitante)[1] += golesVisitante - golesLocal;
        }
        return Comparator.comparingInt((Fila fila) -> mini.get(fila.club.getId())[0])
                .thenComparingInt(fila -> mini.get(fila.club.getId())[1])
                .reversed();
    }

    /**
     * El sorteo tiene que dar siempre lo mismo, o la tabla cambiaría en cada
     * consulta: es un número fijo por categoría y club.
     */
    private long sorteo(TorneoCategoria categoria, Club club) {
        // SplitMix64: mezcla los dos ids para que el orden no siga al de los ids.
        long x = categoria.getId() * 0x9E3779B97F4A7C15L + club.getId();
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }

    private static int puntos(int aFavor, int enContra, Reglamento reglamento) {
        if (aFavor > enContra) {
            return reglamento.getPuntosVictoria();
        }
        return aFavor == enContra ? reglamento.getPuntosEmpate() : reglamento.getPuntosDerrota();
    }

    /** Parte la lista, ya ordenada por el criterio, en grupos de iguales. */
    private List<List<Fila>> bloques(List<Fila> filas, Comparator<Fila> criterio) {
        List<Fila> ordenadas = filas.stream().sorted(criterio).toList();
        List<List<Fila>> bloques = new ArrayList<>();
        for (Fila fila : ordenadas) {
            List<Fila> ultimo = bloques.isEmpty() ? null : bloques.get(bloques.size() - 1);
            if (ultimo != null && criterio.compare(ultimo.get(0), fila) == 0) {
                ultimo.add(fila);
            } else {
                List<Fila> nuevo = new ArrayList<>();
                nuevo.add(fila);
                bloques.add(nuevo);
            }
        }
        return bloques;
    }

    /** Fair play: 1 punto por amarilla y 3 por roja, de los partidos que cuentan para la tabla. */
    private void sumarFairPlay(Map<Long, Fila> filas, List<Partido> cerrados) {
        if (cerrados.isEmpty()) {
            return;
        }
        List<Long> ids = cerrados.stream().map(Partido::getId).toList();
        for (EventoPartido evento : eventos.findByPartidoIdIn(ids)) {
            Fila fila = filas.get(evento.getClub().getId());
            if (fila == null) {
                continue;
            }
            if (evento.getTipo() == TipoEventoPartido.AMARILLA) {
                fila.fairPlay += PUNTOS_AMARILLA;
            } else if (evento.getTipo() == TipoEventoPartido.ROJA) {
                fila.fairPlay += PUNTOS_ROJA;
            }
        }
    }

    private int goles(Short goles) {
        return goles == null ? 0 : goles;
    }

    /** Lo que se va acumulando de un equipo. */
    private static final class Fila {
        private final Club club;
        private int puntos;
        private int jugados;
        private int ganados;
        private int empatados;
        private int perdidos;
        private int golesFavor;
        private int golesContra;
        private int fairPlay;

        private Fila(Club club) {
            this.club = club;
        }

        private void sumar(int aFavor, int enContra, Reglamento reglamento) {
            jugados++;
            golesFavor += aFavor;
            golesContra += enContra;
            puntos += TablaPosicionesService.puntos(aFavor, enContra, reglamento);
            if (aFavor > enContra) {
                ganados++;
            } else if (aFavor == enContra) {
                empatados++;
            } else {
                perdidos++;
            }
        }

        private int diferencia() {
            return golesFavor - golesContra;
        }

        private FilaTablaResponseDto toResponse(int posicion) {
            return new FilaTablaResponseDto(posicion, club.getId(), club.getNombreClub(), puntos,
                    jugados, ganados, empatados, perdidos, golesFavor, golesContra, diferencia(),
                    fairPlay);
        }
    }
}
