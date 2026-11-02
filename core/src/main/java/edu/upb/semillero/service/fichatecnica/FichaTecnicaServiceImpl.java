package edu.upb.semillero.service.fichatecnica;

import edu.upb.semillero.repository.entity.DocumentoJugador;
import edu.upb.semillero.repository.entity.Fichado;
import edu.upb.semillero.repository.entity.Jugador;
import edu.upb.semillero.repository.enums.EstadoDocumentoJugador;
import edu.upb.semillero.repository.enums.TipoDocumentoJugador;
import edu.upb.semillero.repository.repos.DocumentoJugadorRepository;
import edu.upb.semillero.repository.repos.FichadoRepository;
import edu.upb.semillero.repository.repos.JugadorRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación real de {@code FichaTecnicaService} (punto 7 y 10 del acuerdo
 * S1-04). Lee de la BD los hechos que torneos necesita para la lista de buena
 * fe (HU 5): la edad sale de {@code jugadores.fecha_nacimiento}, el estado de
 * {@code jugadores.estado}, el fichaje de {@code fichado} y, desde la migración
 * V011, los documentos de {@code documentos_jugador}.
 *
 * <p>Al ser un {@code @Service}, reemplaza automáticamente al stub
 * {@code MocksExternosConfiguration} (que estaba anotado
 * {@code @ConditionalOnMissingBean}). Solo lee: nunca escribe en estas tablas.
 *
 * <p>Reglas de vigencia de documentos (HU 5):
 * <ul>
 *   <li><strong>documentosVigentes</strong>: el jugador tiene, VIGENTE y no
 *       vencido a la fecha de corte, su CI y su certificado de nacimiento.</li>
 *   <li><strong>aptoMedicoVigente</strong>: tiene un apto médico VIGENTE y no
 *       vencido a la fecha de corte.</li>
 * </ul>
 * Un documento está vigente si su estado es {@code VIGENTE} y su fecha de
 * vencimiento es nula o no es anterior a la fecha consultada.
 */
@Service
@Transactional(readOnly = true)
public class FichaTecnicaServiceImpl implements FichaTecnicaService {

    /** Documentos de identidad que la HU 5 exige para habilitar a un jugador. */
    private static final Set<TipoDocumentoJugador> DOCUMENTOS_REQUERIDOS =
            Set.of(TipoDocumentoJugador.CI, TipoDocumentoJugador.CERTIFICADO_NACIMIENTO);

    /** La edad que se devuelve cuando el jugador no tiene fecha de nacimiento. */
    public static final int EDAD_DESCONOCIDA = 0;

    private final JugadorRepository jugadores;
    private final FichadoRepository fichados;
    private final DocumentoJugadorRepository documentos;

    public FichaTecnicaServiceImpl(JugadorRepository jugadores, FichadoRepository fichados,
            DocumentoJugadorRepository documentos) {
        this.jugadores = jugadores;
        this.fichados = fichados;
        this.documentos = documentos;
    }

    @Override
    public Optional<JugadorParaTorneosDto> datosParaTorneos(Long jugadorId, LocalDate fechaCorte) {
        if (jugadorId == null) {
            return Optional.empty();
        }
        LocalDate corte = fechaCorte == null ? LocalDate.now() : fechaCorte;
        return jugadores.findById(jugadorId).map(jugador -> {
            List<DocumentoJugador> docs = documentos.findByJugadorId(jugador.getId());
            return new JugadorParaTorneosDto(
                    jugador.getId(),
                    edadA(jugador, corte),
                    jugador.getEstado(),
                    documentosVigentes(docs, corte),
                    aptoMedicoVigente(docs, corte),
                    clubesDondeEstaFichado(jugador.getId(), corte));
        });
    }

    @Override
    public List<Long> jugadoresFichadosEn(Long clubId, LocalDate fecha) {
        if (clubId == null) {
            return List.of();
        }
        LocalDate dia = fecha == null ? LocalDate.now() : fecha;
        return fichados.findByClubId(clubId).stream()
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

    private boolean documentosVigentes(List<DocumentoJugador> docs, LocalDate fecha) {
        return DOCUMENTOS_REQUERIDOS.stream()
                .allMatch(tipo -> docs.stream()
                        .anyMatch(doc -> doc.getTipo() == tipo && vigente(doc, fecha)));
    }

    private boolean aptoMedicoVigente(List<DocumentoJugador> docs, LocalDate fecha) {
        return docs.stream()
                .anyMatch(doc -> doc.getTipo() == TipoDocumentoJugador.APTO_MEDICO && vigente(doc, fecha));
    }

    /** Un documento vale si está VIGENTE y no venció a la fecha consultada. */
    private boolean vigente(DocumentoJugador doc, LocalDate fecha) {
        if (doc.getEstado() != EstadoDocumentoJugador.VIGENTE) {
            return false;
        }
        LocalDate vencimiento = doc.getFechaVencimiento();
        return vencimiento == null || !vencimiento.isBefore(fecha);
    }

    private Set<Long> clubesDondeEstaFichado(Long jugadorId, LocalDate fecha) {
        return fichados.findByJugadorId(jugadorId).stream()
                .filter(fichado -> vigente(fichado, fecha))
                .map(fichado -> fichado.getClub() == null ? null : fichado.getClub().getId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Un fichaje vale a una fecha si ya empezó y todavía no terminó. Las dos
     * columnas de {@code fichado} aceptan NULL, y un NULL se toma como abierto
     * de ese lado.
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
