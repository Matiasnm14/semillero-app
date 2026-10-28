package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.dto.request.torneos.AjustePartidoRequestDto;
import edu.upb.semillero.repository.dto.request.torneos.GenerarFixtureRequestDto;
import edu.upb.semillero.repository.dto.request.torneos.PartidoManualRequestDto;
import edu.upb.semillero.repository.dto.response.torneos.FixturePublicadoResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.PartidoFixtureResponseDto;
import edu.upb.semillero.repository.entity.Auditoria;
import edu.upb.semillero.repository.entity.Club;
import edu.upb.semillero.repository.entity.Partido;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.entity.torneos.Cancha;
import edu.upb.semillero.repository.entity.torneos.Grupo;
import edu.upb.semillero.repository.entity.torneos.InscripcionEquipo;
import edu.upb.semillero.repository.entity.torneos.Reglamento;
import edu.upb.semillero.repository.entity.torneos.Torneo;
import edu.upb.semillero.repository.entity.torneos.TorneoCategoria;
import edu.upb.semillero.repository.enums.EstadoPartido;
import edu.upb.semillero.repository.enums.FaseTorneo;
import edu.upb.semillero.repository.enums.torneos.AmbitoTorneo;
import edu.upb.semillero.repository.enums.torneos.EstadoInscripcion;
import edu.upb.semillero.repository.enums.torneos.EstadoTorneo;
import edu.upb.semillero.repository.enums.torneos.FormatoTorneo;
import edu.upb.semillero.repository.repos.AuditoriaRepository;
import edu.upb.semillero.repository.repos.torneos.CanchaRepository;
import edu.upb.semillero.repository.repos.torneos.InscripcionEquipoRepository;
import edu.upb.semillero.repository.repos.torneos.PartidoFixtureRepository;
import edu.upb.semillero.repository.repos.torneos.ReglamentoRepository;
import edu.upb.semillero.repository.repos.torneos.TorneoCategoriaRepository;
import edu.upb.semillero.repository.repos.torneos.TorneoRepository;
import edu.upb.semillero.service.EntityRelations;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.ConflictException;
import edu.upb.semillero.service.exception.NotFoundException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU 7 - Generar el fixture (tareas S2-14 a S2-18).
 *
 * <p>Qué hace cada parte:
 *
 * <ul>
 *   <li><strong>S2-14</strong>: liga por el método de rotación, n − 1 fechas
 *       con n equipos y un equipo libre por fecha si n es impar. En formato de
 *       grupos se arma una rotación por grupo.</li>
 *   <li><strong>S2-15</strong>: llaves de eliminación directa, con siembra por
 *       cabeza de serie o por sorteo, y los que pasan de ronda cuando los
 *       partidos se cierran.</li>
 *   <li><strong>S2-16</strong>: cancha y horario sin choques: una cancha no
 *       tiene dos partidos cruzados, un equipo no juega dos a la vez y entre
 *       dos partidos suyos pasa el descanso mínimo del reglamento.</li>
 *   <li><strong>S2-17</strong>: ajuste manual de un partido y carga manual, que
 *       es la única forma de cargar los partidos de un campeonato externo.</li>
 *   <li><strong>S2-18</strong>: publicar el fixture; el PDF lo arma
 *       {@code FixturePdfService}.</li>
 * </ul>
 *
 * <p>Solo entran al fixture los equipos con la inscripción APROBADA (HU 6).
 */
@Service
@Transactional(readOnly = true)
public class FixtureService {
    private static final String MODULO = "TORNEOS";
    private static final String TABLA = "partidos";
    /** Cuántos horarios se prueban por día antes de rendirse. */
    private static final int SLOTS_POR_DIA = 12;
    private static final LocalTime PRIMER_PARTIDO = LocalTime.of(9, 0);
    /** Ningún partido del fixture termina después de esta hora. */
    private static final LocalTime ULTIMO_PARTIDO = LocalTime.of(22, 0);
    private static final int DIAS_ENTRE_JORNADAS = 7;
    private static final int MINUTOS_ENTRE_PARTIDOS = 15;
    private static final int ENTRETIEMPO_MIN = 15;
    /** Más de 16 equipos no entran: no hay fase anterior a octavos en FaseTorneo. */
    private static final int MAXIMO_EQUIPOS_ELIMINACION = 16;

    private final PartidoFixtureRepository partidos;
    private final TorneoRepository torneos;
    private final TorneoCategoriaRepository categorias;
    private final InscripcionEquipoRepository inscripciones;
    private final ReglamentoRepository reglamentos;
    private final CanchaRepository canchas;
    private final AuditoriaRepository auditoria;
    private final EntityRelations relations;

    public FixtureService(PartidoFixtureRepository partidos, TorneoRepository torneos,
            TorneoCategoriaRepository categorias, InscripcionEquipoRepository inscripciones,
            ReglamentoRepository reglamentos, CanchaRepository canchas,
            AuditoriaRepository auditoria, EntityRelations relations) {
        this.partidos = partidos;
        this.torneos = torneos;
        this.categorias = categorias;
        this.inscripciones = inscripciones;
        this.reglamentos = reglamentos;
        this.canchas = canchas;
        this.auditoria = auditoria;
        this.relations = relations;
    }

    /** HU 15: un partido de torneo por su ID, con la cancha y su superficie. */
    public PartidoFixtureResponseDto findPartido(Long partidoId) {
        Partido partido = partidos.findById(partidoId)
                .filter(p -> p.getTorneoCategoria() != null)
                .orElseThrow(() -> new NotFoundException("Partido de torneo con id " + partidoId
                        + " no existe"));
        return toResponse(partido);
    }

    /**
     * HU 14: los partidos que vienen de una categoría, PROGRAMADO o EN_CURSO,
     * del más cercano al más lejano. Con {@code clubId}, solo los de ese equipo.
     */
    public List<PartidoFixtureResponseDto> proximos(Long categoriaId, Long clubId, LocalDateTime desde) {
        return partidos.findByTorneoCategoriaIdOrderByJornadaAscFechaInicioAsc(categoriaId).stream()
                .filter(p -> p.getEstado() == EstadoPartido.PROGRAMADO
                        || p.getEstado() == EstadoPartido.EN_CURSO)
                .filter(p -> !ChoquesDeAgenda.finDe(p).isBefore(desde))
                .filter(p -> clubId == null || Objects.equals(p.getClubLocal().getId(), clubId)
                        || Objects.equals(p.getClubVisitante().getId(), clubId))
                .sorted(Comparator.comparing(Partido::getFechaInicio))
                .map(this::toResponse)
                .toList();
    }

    public List<PartidoFixtureResponseDto> findByCategoria(Long torneoId, Long categoriaId) {
        TorneoCategoria categoria = requireCategoria(torneoId, categoriaId);
        return partidos.findByTorneoCategoriaIdOrderByJornadaAscFechaInicioAsc(categoria.getId())
                .stream().map(this::toResponse).toList();
    }

    /** Tarea S2-14: liga por rotación, o una rotación por grupo si el formato es de grupos. */
    @Transactional
    public List<PartidoFixtureResponseDto> generarLiga(Long torneoId, Long categoriaId,
            GenerarFixtureRequestDto request) {
        TorneoCategoria categoria = requireCategoria(torneoId, categoriaId);
        Torneo torneo = categoria.getTorneo();
        requireFixtureGenerable(torneo, categoria);
        if (torneo.getFormato() == FormatoTorneo.ELIMINACION_DIRECTA) {
            throw new BusinessException("El torneo " + torneo.getNombre()
                    + " es de eliminación directa: el fixture se genera con /fixture/eliminacion");
        }
        List<InscripcionEquipo> equipos = equiposAprobados(categoria);
        Agenda agenda = agenda(categoria, request);
        boolean idaVuelta = request.idaVuelta() != null
                ? request.idaVuelta()
                : Boolean.TRUE.equals(torneo.getIdaVuelta());

        List<Partido> generados = new ArrayList<>();
        if (torneo.getFormato() == FormatoTorneo.GRUPOS_FASE_FINAL) {
            for (Map.Entry<Grupo, List<InscripcionEquipo>> grupo : porGrupo(equipos).entrySet()) {
                generados.addAll(rotacion(categoria, grupo.getKey(), grupo.getValue(),
                        FaseTorneo.GRUPOS, idaVuelta));
            }
        } else {
            generados.addAll(rotacion(categoria, null, equipos, FaseTorneo.LIGA, idaVuelta));
        }
        agendarYGuardar(generados, agenda, request);
        registrar("GENERAR_FIXTURE", categoria.getId(), generados.size(), request.usuarioId());
        return generados.stream().map(this::toResponse).toList();
    }

    /** Tarea S2-15: llaves de eliminación directa, por siembra o por sorteo. */
    @Transactional
    public List<PartidoFixtureResponseDto> generarEliminacion(Long torneoId, Long categoriaId,
            GenerarFixtureRequestDto request) {
        TorneoCategoria categoria = requireCategoria(torneoId, categoriaId);
        Torneo torneo = categoria.getTorneo();
        requireFixtureGenerable(torneo, categoria);
        if (torneo.getFormato() == FormatoTorneo.LIGA) {
            throw new BusinessException("El torneo " + torneo.getNombre()
                    + " es de liga: el fixture se genera con /fixture/liga");
        }
        List<InscripcionEquipo> equipos = equiposAprobados(categoria);
        if (equipos.size() > MAXIMO_EQUIPOS_ELIMINACION) {
            throw new BusinessException("La eliminación directa admite hasta "
                    + MAXIMO_EQUIPOS_ELIMINACION + " equipos y la categoría tiene " + equipos.size());
        }
        List<InscripcionEquipo> ordenados = ordenarParaLlaves(equipos, request);
        int llave = potenciaDeDos(ordenados.size());
        int byes = llave - ordenados.size();
        FaseTorneo fase = faseDe(llave);

        // Los primeros sembrados pasan de ronda; el resto se cruza mejor contra peor.
        List<InscripcionEquipo> juegan = new ArrayList<>(ordenados.subList(byes, ordenados.size()));
        List<Partido> generados = new ArrayList<>();
        short jornada = 1;
        for (int i = 0; i < juegan.size() / 2; i++) {
            generados.add(nuevoPartido(categoria, null, fase, jornada,
                    juegan.get(i).getClub(), juegan.get(juegan.size() - 1 - i).getClub()));
        }
        Agenda agenda = agenda(categoria, request);
        agendarYGuardar(generados, agenda, request);
        registrar("GENERAR_LLAVES", categoria.getId(), generados.size(), request.usuarioId());
        return generados.stream().map(this::toResponse).toList();
    }

    /**
     * Tarea S2-15: arma la ronda siguiente con los ganadores de la última, más
     * los equipos que pasaron sin jugar. Pide que la ronda anterior esté
     * cerrada, porque antes no se sabe quién pasa.
     */
    @Transactional
    public List<PartidoFixtureResponseDto> avanzarRonda(Long torneoId, Long categoriaId,
            GenerarFixtureRequestDto request) {
        TorneoCategoria categoria = requireCategoria(torneoId, categoriaId);
        List<Partido> jugados = partidos.findByTorneoCategoriaIdOrderByJornadaAscFechaInicioAsc(
                categoria.getId());
        if (jugados.isEmpty()) {
            throw new BusinessException("La categoría " + categoria.getNombre()
                    + " todavía no tiene llaves generadas");
        }
        FaseTorneo ultima = ultimaFase(jugados);
        List<Partido> ronda = jugados.stream().filter(p -> p.getFase() == ultima).toList();
        for (Partido partido : ronda) {
            if (partido.getEstado() != EstadoPartido.FINALIZADO) {
                throw new ConflictException("El partido " + partido.getId() + " de " + ultima
                        + " todavía no está FINALIZADO: la ronda no se puede cerrar");
            }
        }
        List<Club> clasificados = new ArrayList<>(equiposQuePasaronSinJugar(categoria, jugados));
        ronda.forEach(partido -> clasificados.add(ganadorDe(partido)));
        if (clasificados.size() < 2) {
            throw new BusinessException("La categoría " + categoria.getNombre()
                    + " ya tiene campeón: no hay ronda siguiente");
        }
        FaseTorneo fase = faseDe(clasificados.size());
        short jornada = (short) (ultima(jugados) + 1);
        List<Partido> generados = new ArrayList<>();
        for (int i = 0; i < clasificados.size() / 2; i++) {
            generados.add(nuevoPartido(categoria, null, fase, jornada,
                    clasificados.get(i * 2), clasificados.get(i * 2 + 1)));
        }
        Agenda agenda = agenda(categoria, request);
        agendarYGuardar(generados, agenda, request);
        enlazarConLaRondaAnterior(ronda, generados);
        registrar("AVANZAR_RONDA", categoria.getId(), generados.size(), request.usuarioId());
        return generados.stream().map(this::toResponse).toList();
    }

    /** Borra el fixture de una categoría para volver a generarlo; solo si nada empezó. */
    @Transactional
    public void borrar(Long torneoId, Long categoriaId, Long usuarioId) {
        TorneoCategoria categoria = requireCategoria(torneoId, categoriaId);
        List<Partido> actuales = partidos.findByTorneoCategoriaIdOrderByJornadaAscFechaInicioAsc(
                categoria.getId());
        for (Partido partido : actuales) {
            if (partido.getEstado() != EstadoPartido.PROGRAMADO) {
                throw new ConflictException("El partido " + partido.getId() + " está en "
                        + partido.getEstado() + ": el fixture ya empezó y no se borra");
            }
        }
        actuales.forEach(partidos::delete);
        partidos.flush();
        registrar("BORRAR_FIXTURE", categoria.getId(), actuales.size(), usuarioId);
    }

    /**
     * Tarea S2-17: carga manual de un partido. En un campeonato externo es la
     * única forma, porque ahí no se genera fixture.
     */
    @Transactional
    public PartidoFixtureResponseDto cargarManual(Long torneoId, Long categoriaId,
            PartidoManualRequestDto request) {
        TorneoCategoria categoria = requireCategoria(torneoId, categoriaId);
        if (Objects.equals(request.clubLocalId(), request.clubVisitanteId())) {
            throw new BusinessException("Un club no puede jugar contra sí mismo");
        }
        Partido partido = new Partido();
        partido.setTorneoCategoria(categoria);
        partido.setGrupo(relations.find(Grupo.class, request.grupoId()));
        partido.setFase(request.fase() == null ? faseSegunFormato(categoria) : request.fase());
        partido.setJornada(request.jornada());
        partido.setClubLocal(relations.find(Club.class, request.clubLocalId()));
        partido.setClubVisitante(relations.find(Club.class, request.clubVisitanteId()));
        partido.setCancha(relations.find(Cancha.class, request.canchaId()));
        partido.setArbitro(request.arbitro());
        partido.setMesaControl(relations.find(Usuario.class, request.mesaControlId()));
        partido.setEstado(EstadoPartido.PROGRAMADO);
        partido.setFechaInicio(request.fechaInicio());
        partido.setFechaFin(request.fechaInicio().plusMinutes(
                duracion(categoria, request.duracionPartidoMin())));
        validarSinChoques(partido, ocupadosEseDia(partido.getFechaInicio().toLocalDate()),
                descansoMinimo(categoria));
        Partido guardado = partidos.saveAndFlush(partido);
        registrar("CARGAR_PARTIDO", categoria.getId(), 1, request.usuarioId());
        return toResponse(guardado);
    }

    /** Tarea S2-17: mover un partido de fecha, hora o cancha, revalidando choques. */
    @Transactional
    public PartidoFixtureResponseDto ajustar(Long partidoId, AjustePartidoRequestDto request) {
        Partido partido = partidos.findById(partidoId)
                .orElseThrow(() -> new NotFoundException("Partido con id " + partidoId + " no existe"));
        if (partido.getTorneoCategoria() == null) {
            throw new BusinessException("El partido " + partidoId
                    + " no es de un torneo: no se ajusta desde el fixture");
        }
        if (partido.getEstado() != EstadoPartido.PROGRAMADO) {
            throw new ConflictException("El partido " + partidoId + " está en " + partido.getEstado()
                    + " y ya no se puede mover");
        }
        partido.setFechaInicio(request.fechaInicio());
        partido.setFechaFin(request.fechaInicio().plusMinutes(
                duracion(partido.getTorneoCategoria(), request.duracionPartidoMin())));
        if (request.canchaId() != null) {
            partido.setCancha(relations.find(Cancha.class, request.canchaId()));
        }
        if (request.arbitro() != null) {
            partido.setArbitro(request.arbitro());
        }
        if (request.mesaControlId() != null) {
            partido.setMesaControl(relations.find(Usuario.class, request.mesaControlId()));
        }
        validarSinChoques(partido, ocupadosEseDia(partido.getFechaInicio().toLocalDate()),
                descansoMinimo(partido.getTorneoCategoria()));
        Partido guardado = partidos.saveAndFlush(partido);
        registrar("AJUSTAR_PARTIDO", partido.getTorneoCategoria().getId(), 1, request.usuarioId());
        return toResponse(guardado);
    }

    /**
     * Tarea S2-18: publicar. El torneo pasa a EN_CURSO y desde ahí el fixture
     * es el oficial para los equipos y la app.
     */
    @Transactional
    public FixturePublicadoResponseDto publicar(Long torneoId, Long usuarioId) {
        Torneo torneo = requireTorneo(torneoId);
        if (torneo.getEstado() == EstadoTorneo.EN_CURSO) {
            throw new ConflictException("El fixture del torneo " + torneo.getNombre()
                    + " ya está publicado");
        }
        if (torneo.getEstado() != EstadoTorneo.INSCRIPCIONES_ABIERTAS) {
            throw new BusinessException("El torneo " + torneo.getNombre() + " está en "
                    + torneo.getEstado() + ": solo se publica el fixture de un torneo con las"
                    + " inscripciones abiertas");
        }
        List<TorneoCategoria> delTorneo = categorias.findByTorneoIdOrderByIdAsc(torneoId);
        long total = delTorneo.stream()
                .mapToLong(categoria -> partidos.countByTorneoCategoriaId(categoria.getId()))
                .sum();
        if (total == 0) {
            throw new BusinessException("El torneo " + torneo.getNombre()
                    + " no tiene ningún partido cargado: no hay fixture para publicar");
        }
        torneo.setEstado(EstadoTorneo.EN_CURSO);
        torneos.saveAndFlush(torneo);
        registrar("PUBLICAR_FIXTURE", torneoId, (int) total, usuarioId);
        return new FixturePublicadoResponseDto(torneoId, torneo.getEstado(), total,
                delTorneo.size());
    }

    private TorneoCategoria requireCategoria(Long torneoId, Long categoriaId) {
        requireTorneo(torneoId);
        return categorias.findByIdAndTorneoId(categoriaId, torneoId)
                .orElseThrow(() -> new NotFoundException("La categoría con id " + categoriaId
                        + " no existe en el torneo " + torneoId));
    }

    private Torneo requireTorneo(Long torneoId) {
        return torneos.findById(torneoId)
                .orElseThrow(() -> new NotFoundException("Torneo con id " + torneoId + " no existe"));
    }

    private void requireFixtureGenerable(Torneo torneo, TorneoCategoria categoria) {
        if (torneo.getAmbito() == AmbitoTorneo.EXTERNO) {
            throw new BusinessException("El torneo " + torneo.getNombre()
                    + " es un campeonato externo: sus partidos se cargan a mano, no se genera"
                    + " fixture");
        }
        if (partidos.countByTorneoCategoriaId(categoria.getId()) > 0) {
            throw new ConflictException("La categoría " + categoria.getNombre()
                    + " ya tiene fixture; hay que borrarlo antes de volver a generarlo");
        }
    }

    private List<InscripcionEquipo> equiposAprobados(TorneoCategoria categoria) {
        List<InscripcionEquipo> equipos = inscripciones
                .findByTorneoCategoriaIdAndEstadoOrderByIdAsc(categoria.getId(),
                        EstadoInscripcion.APROBADA);
        if (equipos.size() < 2) {
            throw new BusinessException("La categoría " + categoria.getNombre() + " tiene "
                    + equipos.size() + " equipo(s) con la inscripción aprobada: hacen falta al"
                    + " menos 2 para generar el fixture");
        }
        return equipos;
    }

    private Map<Grupo, List<InscripcionEquipo>> porGrupo(List<InscripcionEquipo> equipos) {
        Map<Grupo, List<InscripcionEquipo>> porGrupo = new LinkedHashMap<>();
        for (InscripcionEquipo equipo : equipos) {
            if (equipo.getGrupo() == null) {
                throw new BusinessException("El equipo de la inscripción " + equipo.getId()
                        + " no tiene grupo asignado y el torneo se juega por grupos");
            }
            porGrupo.computeIfAbsent(equipo.getGrupo(), grupo -> new ArrayList<>()).add(equipo);
        }
        porGrupo.forEach((grupo, delGrupo) -> {
            if (delGrupo.size() < 2) {
                throw new BusinessException("El grupo " + grupo.getNombre()
                        + " tiene un solo equipo aprobado");
            }
        });
        return porGrupo;
    }

    /**
     * Método de rotación: se fija el primer equipo y los demás giran. Con n
     * equipos salen n − 1 fechas; si n es impar se agrega un lugar vacío y el
     * equipo que queda enfrentado a él descansa esa fecha.
     */
    private List<Partido> rotacion(TorneoCategoria categoria, Grupo grupo,
            List<InscripcionEquipo> equipos, FaseTorneo fase, boolean idaVuelta) {
        List<Club> clubes = new ArrayList<>(equipos.stream().map(InscripcionEquipo::getClub).toList());
        if (clubes.size() % 2 != 0) {
            clubes.add(null);
        }
        int n = clubes.size();
        List<Partido> generados = new ArrayList<>();
        short jornada = 1;
        for (int fecha = 0; fecha < n - 1; fecha++) {
            for (int i = 0; i < n / 2; i++) {
                Club local = clubes.get(i);
                Club visitante = clubes.get(n - 1 - i);
                if (local == null || visitante == null) {
                    continue;
                }
                boolean invertir = fecha % 2 == 1;
                generados.add(nuevoPartido(categoria, grupo, fase, jornada,
                        invertir ? visitante : local, invertir ? local : visitante));
            }
            Collections.rotate(clubes.subList(1, n), 1);
            jornada++;
        }
        if (idaVuelta) {
            int jornadasIda = n - 1;
            List<Partido> vuelta = generados.stream()
                    .map(ida -> nuevoPartido(categoria, grupo, fase,
                            (short) (jornadasIda + ida.getJornada()),
                            ida.getClubVisitante(), ida.getClubLocal()))
                    .toList();
            generados.addAll(vuelta);
        }
        return generados;
    }

    private List<InscripcionEquipo> ordenarParaLlaves(List<InscripcionEquipo> equipos,
            GenerarFixtureRequestDto request) {
        List<InscripcionEquipo> ordenados = new ArrayList<>(equipos);
        boolean siembra = request.siembra() == null || request.siembra();
        if (siembra) {
            ordenados.sort(Comparator
                    .comparing(InscripcionEquipo::getCabezaSerie,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(InscripcionEquipo::getId));
        } else {
            Collections.shuffle(ordenados, request.semillaSorteo() == null
                    ? new Random()
                    : new Random(request.semillaSorteo()));
        }
        return ordenados;
    }

    private int potenciaDeDos(int equipos) {
        int llave = 2;
        while (llave < equipos) {
            llave *= 2;
        }
        return llave;
    }

    private FaseTorneo faseDe(int equipos) {
        return switch (potenciaDeDos(equipos)) {
            case 2 -> FaseTorneo.FINAL;
            case 4 -> FaseTorneo.SEMIFINAL;
            case 8 -> FaseTorneo.CUARTOS;
            case 16 -> FaseTorneo.OCTAVOS;
            default -> throw new BusinessException("No hay una fase definida para " + equipos
                    + " equipos; el máximo son " + MAXIMO_EQUIPOS_ELIMINACION);
        };
    }

    private FaseTorneo faseSegunFormato(TorneoCategoria categoria) {
        return switch (categoria.getTorneo().getFormato()) {
            case LIGA -> FaseTorneo.LIGA;
            case GRUPOS_FASE_FINAL -> FaseTorneo.GRUPOS;
            case ELIMINACION_DIRECTA -> FaseTorneo.FINAL;
        };
    }

    private FaseTorneo ultimaFase(List<Partido> jugados) {
        return jugados.stream()
                .max(Comparator.comparing(Partido::getJornada,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(Partido::getFase)
                .orElseThrow(() -> new BusinessException("El fixture no tiene fases cargadas"));
    }

    private short ultima(List<Partido> jugados) {
        return jugados.stream()
                .map(Partido::getJornada)
                .filter(Objects::nonNull)
                .max(Short::compareTo)
                .orElse((short) 0);
    }

    /** Los que entraron con bye: aprobados que no aparecen en ningún partido. */
    private List<Club> equiposQuePasaronSinJugar(TorneoCategoria categoria, List<Partido> jugados) {
        if (jugados.stream().map(Partido::getFase).distinct().count() > 1) {
            return List.of();
        }
        List<Long> conPartido = jugados.stream()
                .flatMap(partido -> List.of(partido.getClubLocal().getId(),
                        partido.getClubVisitante().getId()).stream())
                .toList();
        return inscripciones
                .findByTorneoCategoriaIdAndEstadoOrderByIdAsc(categoria.getId(),
                        EstadoInscripcion.APROBADA)
                .stream()
                .map(InscripcionEquipo::getClub)
                .filter(club -> !conPartido.contains(club.getId()))
                .toList();
    }

    private Club ganadorDe(Partido partido) {
        if (partido.getGanadorWalkover() != null) {
            return partido.getGanadorWalkover();
        }
        int local = partido.getGolesLocal() == null ? 0 : partido.getGolesLocal();
        int visitante = partido.getGolesVisitante() == null ? 0 : partido.getGolesVisitante();
        if (local != visitante) {
            return local > visitante ? partido.getClubLocal() : partido.getClubVisitante();
        }
        int penalesLocal = partido.getPenalesLocal() == null ? 0 : partido.getPenalesLocal();
        int penalesVisitante = partido.getPenalesVisitante() == null ? 0 : partido.getPenalesVisitante();
        if (penalesLocal == penalesVisitante) {
            throw new BusinessException("El partido " + partido.getId()
                    + " terminó empatado y sin penales: no se sabe quién pasa de ronda");
        }
        return penalesLocal > penalesVisitante ? partido.getClubLocal() : partido.getClubVisitante();
    }

    private void enlazarConLaRondaAnterior(List<Partido> anterior, List<Partido> siguiente) {
        for (Partido partido : anterior) {
            Club ganador = ganadorDe(partido);
            siguiente.stream()
                    .filter(nuevo -> Objects.equals(nuevo.getClubLocal().getId(), ganador.getId())
                            || Objects.equals(nuevo.getClubVisitante().getId(), ganador.getId()))
                    .findFirst()
                    .ifPresent(nuevo -> {
                        partido.setPartidoSiguiente(nuevo);
                        partidos.save(partido);
                    });
        }
        partidos.flush();
    }

    private Partido nuevoPartido(TorneoCategoria categoria, Grupo grupo, FaseTorneo fase,
            short jornada, Club local, Club visitante) {
        Partido partido = new Partido();
        partido.setTorneoCategoria(categoria);
        partido.setGrupo(grupo);
        partido.setFase(fase);
        partido.setJornada(jornada);
        partido.setClubLocal(local);
        partido.setClubVisitante(visitante);
        partido.setEstado(EstadoPartido.PROGRAMADO);
        return partido;
    }

    private Agenda agenda(TorneoCategoria categoria, GenerarFixtureRequestDto request) {
        List<Cancha> disponibles = request.canchaIds().stream()
                .map(id -> canchas.findById(id).orElseThrow(() -> new NotFoundException(
                        "La cancha con id " + id + " no existe")))
                .peek(cancha -> {
                    if (!Boolean.TRUE.equals(cancha.getActiva())) {
                        throw new BusinessException("La cancha " + cancha.getNombre()
                                + " está inactiva");
                    }
                })
                .toList();
        return new Agenda(disponibles,
                duracion(categoria, request.duracionPartidoMin()),
                request.minutosEntrePartidos() == null
                        ? MINUTOS_ENTRE_PARTIDOS : request.minutosEntrePartidos(),
                descansoMinimo(categoria),
                request.horaPrimerPartido() == null ? PRIMER_PARTIDO : request.horaPrimerPartido(),
                request.horaUltimoPartido() == null ? ULTIMO_PARTIDO : request.horaUltimoPartido(),
                request.diasEntreJornadas() == null
                        ? DIAS_ENTRE_JORNADAS : request.diasEntreJornadas());
    }

    private int duracion(TorneoCategoria categoria, Integer pedida) {
        return pedida != null ? pedida : categoria.getDuracionTiempoMin() * 2 + ENTRETIEMPO_MIN;
    }

    private int descansoMinimo(TorneoCategoria categoria) {
        return reglamentos.findByTorneoId(categoria.getTorneo().getId())
                .map(Reglamento::getDescansoMinimoMin)
                .map(Short::intValue)
                .orElse(0);
    }

    /**
     * Tarea S2-16: a cada partido se le busca cancha y horario libres. Se
     * prueban los horarios de la fecha uno por uno, y en cada uno todas las
     * canchas, hasta encontrar un hueco donde no choque nada.
     */
    private void agendarYGuardar(List<Partido> generados, Agenda agenda,
            GenerarFixtureRequestDto request) {
        // La jornada más baja de esta tanda es la que cae en fechaPrimeraJornada:
        // así sirve igual para la primera fecha de la liga y para una ronda nueva
        // de las llaves, que arranca en una jornada más alta.
        short jornadaBase = generados.stream().map(Partido::getJornada)
                .min(Short::compareTo).orElse((short) 1);
        List<Partido> ocupados = new ArrayList<>();
        short jornadaActual = 0;
        LocalDate dia = request.fechaPrimeraJornada();
        for (Partido partido : generados) {
            if (partido.getJornada() != jornadaActual) {
                jornadaActual = partido.getJornada();
                dia = request.fechaPrimeraJornada()
                        .plusDays((long) (jornadaActual - jornadaBase) * agenda.diasEntreJornadas());
                ocupados = new ArrayList<>(ocupadosEseDia(dia));
            }
            agendar(partido, dia, agenda, ocupados);
            ocupados.add(partido);
        }
        generados.forEach(partidos::save);
        partidos.flush();
    }

    private void agendar(Partido partido, LocalDate dia, Agenda agenda, List<Partido> ocupados) {
        int paso = agenda.duracionMin() + agenda.intervaloMin();
        LocalDateTime cierre = dia.atTime(agenda.horaUltimoPartido());
        for (int slot = 0; slot < SLOTS_POR_DIA; slot++) {
            LocalDateTime inicio = dia.atTime(agenda.horaPrimerPartido()).plusMinutes((long) slot * paso);
            LocalDateTime fin = inicio.plusMinutes(agenda.duracionMin());
            if (fin.isAfter(cierre)) {
                break;
            }
            for (Cancha cancha : agenda.canchas()) {
                partido.setCancha(cancha);
                partido.setFechaInicio(inicio);
                partido.setFechaFin(fin);
                if (ChoquesDeAgenda.sinChoques(partido, ocupados, agenda.descansoMin())) {
                    return;
                }
            }
        }
        throw new BusinessException("No hay cancha ni horario libres el " + dia + " para "
                + partido.getClubLocal().getNombreClub() + " contra "
                + partido.getClubVisitante().getNombreClub() + ": probá con más canchas, otra hora"
                + " de inicio o más días entre jornadas");
    }

    private List<Partido> ocupadosEseDia(LocalDate dia) {
        return partidos.findByFechaInicioBetween(dia.atStartOfDay(), dia.atTime(LocalTime.MAX))
                .stream()
                .filter(partido -> partido.getEstado() != EstadoPartido.CANCELADO)
                .toList();
    }

    private void validarSinChoques(Partido partido, List<Partido> ocupados, int descansoMin) {
        if (!ChoquesDeAgenda.sinChoques(partido, ocupados, descansoMin)) {
            throw new ConflictException("El partido choca con otro ya programado: revisá la cancha,"
                    + " el horario y el descanso mínimo entre partidos del mismo equipo");
        }
    }

    private void registrar(String accion, Long registroId, int cantidad, Long usuarioId) {
        Auditoria fila = new Auditoria();
        fila.setModulo(MODULO);
        fila.setTabla(TABLA);
        fila.setRegistroId(registroId);
        fila.setAccion(accion);
        fila.setValorNuevo("{\"partidos\":%d}".formatted(cantidad));
        fila.setUsuario(relations.find(Usuario.class, usuarioId));
        fila.setFecha(LocalDateTime.now());
        auditoria.saveAndFlush(fila);
    }

    private PartidoFixtureResponseDto toResponse(Partido partido) {
        Cancha cancha = partido.getCancha();
        return new PartidoFixtureResponseDto(
                partido.getId(),
                relations.id(partido.getTorneoCategoria()),
                relations.id(partido.getGrupo()),
                partido.getFase(),
                partido.getJornada(),
                relations.id(partido.getClubLocal()),
                relations.id(partido.getClubVisitante()),
                relations.id(cancha),
                cancha == null ? null : cancha.getNombre(),
                cancha == null ? null : cancha.getTipoSuperficie(),
                cancha == null ? null : cancha.getDescripcion(),
                partido.getFechaInicio(),
                partido.getFechaFin(),
                partido.getEstado(),
                partido.getArbitro(),
                relations.id(partido.getMesaControl()),
                partido.getGolesLocal(),
                partido.getGolesVisitante(),
                relations.id(partido.getPartidoSiguiente()));
    }

    /** Lo que el generador necesita saber para repartir los partidos. */
    private record Agenda(List<Cancha> canchas, int duracionMin, int intervaloMin, int descansoMin,
            LocalTime horaPrimerPartido, LocalTime horaUltimoPartido, int diasEntreJornadas) {}
}
