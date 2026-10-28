package edu.upb.semillero.service.torneos;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import edu.upb.semillero.repository.dto.response.torneos.CuerpoTecnicoResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.EquipoPlanillaResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.JugadorPlanillaResponseDto;
import edu.upb.semillero.repository.dto.response.torneos.PlanillaResponseDto;
import edu.upb.semillero.repository.entity.Partido;
import edu.upb.semillero.repository.entity.torneos.Cancha;
import edu.upb.semillero.repository.entity.torneos.InscripcionEquipo;
import edu.upb.semillero.repository.entity.torneos.ListaBuenaFe;
import edu.upb.semillero.repository.entity.torneos.Torneo;
import edu.upb.semillero.repository.entity.torneos.TorneoCategoria;
import edu.upb.semillero.repository.repos.torneos.InscripcionEquipoRepository;
import edu.upb.semillero.repository.repos.torneos.ListaBuenaFeRepository;
import edu.upb.semillero.repository.repos.torneos.PartidoFixtureRepository;
import edu.upb.semillero.repository.repos.torneos.TorneoCategoriaRepository;
import edu.upb.semillero.repository.repos.torneos.TorneoRepository;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.NotFoundException;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los PDF que piden las HU: la lista de buena fe confirmada (tarea S2-08), el
 * fixture de una categoría (tarea S2-18) y la planilla del partido (tarea
 * S3-04).
 *
 * <p>Se arman con OpenPDF. Van solo con IDs de jugador: los nombres son datos
 * personales de ficha técnica y torneos no los copia.
 */
@Service
@Transactional(readOnly = true)
public class PdfTorneosService {
    private static final DateTimeFormatter FECHA_Y_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
    private static final Font SUBTITULO = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font CABECERA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font CELDA = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font NOTA = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8);

    private final InscripcionEquipoRepository inscripciones;
    private final ListaBuenaFeRepository listas;
    private final TorneoRepository torneos;
    private final TorneoCategoriaRepository categorias;
    private final PartidoFixtureRepository partidos;

    public PdfTorneosService(InscripcionEquipoRepository inscripciones,
            ListaBuenaFeRepository listas, TorneoRepository torneos,
            TorneoCategoriaRepository categorias, PartidoFixtureRepository partidos) {
        this.inscripciones = inscripciones;
        this.listas = listas;
        this.torneos = torneos;
        this.categorias = categorias;
        this.partidos = partidos;
    }

    /** Tarea S2-08: la lista de buena fe en PDF, para presentarla en la mesa. */
    public byte[] listaBuenaFe(Long inscripcionEquipoId) {
        InscripcionEquipo inscripcion = inscripciones.findById(inscripcionEquipoId)
                .orElseThrow(() -> new NotFoundException(
                        "La inscripción con id " + inscripcionEquipoId + " no existe"));
        List<ListaBuenaFe> jugadores = listas
                .findByInscripcionEquipoIdOrderByDorsalAsc(inscripcionEquipoId);
        if (jugadores.isEmpty()) {
            throw new BusinessException("La lista de buena fe del equipo " + inscripcionEquipoId
                    + " está vacía: no hay nada que exportar");
        }
        TorneoCategoria categoria = inscripcion.getTorneoCategoria();
        Torneo torneo = categoria.getTorneo();

        return documento(PageSize.A4, documento -> {
            documento.add(titulo("Lista de buena fe"));
            documento.add(subtitulo(torneo.getNombre() + " - temporada " + torneo.getTemporada()));
            documento.add(subtitulo("Categoría: " + categoria.getNombre()));
            documento.add(subtitulo("Club: " + inscripcion.getClub().getNombreClub()));
            documento.add(subtitulo("Inscripción: " + inscripcion.getEstado()
                    + " | Lista " + (Boolean.TRUE.equals(inscripcion.getListaConfirmada())
                            ? "confirmada" : "sin confirmar")
                    + " | " + jugadores.size() + " jugadores"));
            documento.add(new Paragraph(" "));

            PdfPTable tabla = tabla(new float[] {1.2f, 2f, 3f, 2f, 5f},
                    "Dorsal", "Jugador", "Posición", "Habilitado", "Observación");
            for (ListaBuenaFe jugador : jugadores) {
                celda(tabla, String.valueOf(jugador.getDorsal()));
                celda(tabla, String.valueOf(jugador.getJugador().getId()));
                celda(tabla, jugador.getPosicion() == null
                        ? "-" : jugador.getPosicion().getPosicion());
                celda(tabla, Boolean.TRUE.equals(jugador.getHabilitado()) ? "Sí" : "No");
                celda(tabla, jugador.getMotivoInhabilitacion() == null
                        ? "" : jugador.getMotivoInhabilitacion());
            }
            documento.add(tabla);
            documento.add(new Paragraph(" "));
            documento.add(new Paragraph("Los jugadores van por su ID: los datos personales son de"
                    + " ficha técnica y el módulo de torneos no los guarda.", NOTA));
        });
    }

    /** Tarea S2-18: el fixture de la categoría en PDF, para publicarlo. */
    public byte[] fixture(Long torneoId, Long categoriaId) {
        Torneo torneo = torneos.findById(torneoId)
                .orElseThrow(() -> new NotFoundException("Torneo con id " + torneoId + " no existe"));
        TorneoCategoria categoria = categorias.findByIdAndTorneoId(categoriaId, torneoId)
                .orElseThrow(() -> new NotFoundException("La categoría con id " + categoriaId
                        + " no existe en el torneo " + torneoId));
        List<Partido> fixture = partidos
                .findByTorneoCategoriaIdOrderByJornadaAscFechaInicioAsc(categoriaId);
        if (fixture.isEmpty()) {
            throw new BusinessException("La categoría " + categoria.getNombre()
                    + " todavía no tiene fixture: no hay nada que exportar");
        }

        return documento(PageSize.A4.rotate(), documento -> {
            documento.add(titulo("Fixture"));
            documento.add(subtitulo(torneo.getNombre() + " - temporada " + torneo.getTemporada()));
            documento.add(subtitulo("Categoría: " + categoria.getNombre()
                    + " | Formato: " + torneo.getFormato() + " | Estado: " + torneo.getEstado()));
            documento.add(new Paragraph(" "));

            PdfPTable tabla = tabla(new float[] {1f, 1.5f, 2.2f, 3f, 3f, 3.5f, 2f},
                    "Fecha", "Fase", "Día y hora", "Local", "Visitante", "Cancha", "Estado");
            for (Partido partido : fixture) {
                Cancha cancha = partido.getCancha();
                celda(tabla, partido.getJornada() == null ? "-" : String.valueOf(partido.getJornada()));
                celda(tabla, String.valueOf(partido.getFase()));
                celda(tabla, partido.getFechaInicio().format(FECHA_Y_HORA));
                celda(tabla, partido.getClubLocal().getNombreClub());
                celda(tabla, partido.getClubVisitante().getNombreClub());
                celda(tabla, cancha == null
                        ? "A definir"
                        : cancha.getNombre() + " (" + cancha.getTipoSuperficie() + ")");
                celda(tabla, String.valueOf(partido.getEstado()));
            }
            documento.add(tabla);
        });
    }

    /**
     * Tarea S3-04: la planilla del partido en PDF. Sirve también en blanco,
     * antes de cargar a nadie, para tener la copia en papel en la mesa.
     */
    public byte[] planilla(PlanillaResponseDto planilla) {
        return documento(PageSize.A4, documento -> {
            documento.add(titulo("Planilla del partido " + planilla.partidoId()));
            documento.add(subtitulo(planilla.torneoNombre() + " | Categoría: "
                    + planilla.categoriaNombre() + " | " + planilla.fase()
                    + (planilla.jornada() == null ? "" : ", fecha " + planilla.jornada())));
            documento.add(subtitulo("Día y hora: " + planilla.fechaInicio().format(FECHA_Y_HORA)
                    + " | Cancha: " + (planilla.canchaNombre() == null
                            ? "a definir"
                            : planilla.canchaNombre() + " (" + planilla.canchaSuperficie() + ")")));
            documento.add(subtitulo(planilla.local().clubNombre() + " vs "
                    + planilla.visitante().clubNombre() + " | Árbitro: "
                    + (planilla.arbitro() == null ? "-" : planilla.arbitro())));
            documento.add(subtitulo("Planilla: " + (planilla.estado() == null
                    ? "sin guardar" : planilla.estado())
                    + " | Partido: " + planilla.estadoPartido()));
            for (EquipoPlanillaResponseDto equipo : List.of(planilla.local(), planilla.visitante())) {
                documento.add(new Paragraph(" "));
                documento.add(subtitulo(equipo.clubNombre() + ": " + equipo.titulares()
                        + " titulares y " + equipo.suplentes() + " suplentes"));
                PdfPTable tabla = tabla(new float[] {1.2f, 2f, 2f, 1.5f, 1.5f, 5f},
                        "Dorsal", "Jugador", "Rol", "Capitán", "Minutos", "Observación");
                for (JugadorPlanillaResponseDto jugador : equipo.jugadores()) {
                    celda(tabla, String.valueOf(jugador.dorsal()));
                    celda(tabla, String.valueOf(jugador.jugadorId()));
                    celda(tabla, !jugador.enPlanilla() ? "-"
                            : Boolean.TRUE.equals(jugador.esTitular()) ? "Titular" : "Suplente");
                    celda(tabla, Boolean.TRUE.equals(jugador.capitan()) ? "Sí" : "");
                    celda(tabla, jugador.minutosJugados() == null
                            ? "" : String.valueOf(jugador.minutosJugados()));
                    celda(tabla, jugador.bloqueado() ? "Bloqueado: " + jugador.motivoBloqueo() : "");
                }
                documento.add(tabla);
                for (CuerpoTecnicoResponseDto tecnico : equipo.cuerpoTecnico()) {
                    documento.add(subtitulo(tecnico.cargo() + ": " + tecnico.nombreCompleto()));
                }
            }
            documento.add(new Paragraph(" "));
            documento.add(subtitulo("Observaciones del árbitro: "
                    + (planilla.observacionesArbitro() == null ? "-" : planilla.observacionesArbitro())));
            documento.add(new Paragraph(" "));
            documento.add(new Paragraph("Los jugadores van por su ID: los datos personales son de"
                    + " ficha técnica y el módulo de torneos no los guarda.", NOTA));
        });
    }

    private byte[] documento(com.lowagie.text.Rectangle tamanio, Contenido contenido) {
        Document documento = new Document(tamanio, 36, 36, 36, 36);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        PdfWriter.getInstance(documento, salida);
        documento.open();
        try {
            contenido.escribir(documento);
        } catch (DocumentException e) {
            throw new BusinessException("No se pudo armar el PDF: " + e.getMessage());
        } finally {
            documento.close();
        }
        return salida.toByteArray();
    }

    private Paragraph titulo(String texto) {
        Paragraph parrafo = new Paragraph(texto, TITULO);
        parrafo.setSpacingAfter(6);
        return parrafo;
    }

    private Paragraph subtitulo(String texto) {
        return new Paragraph(texto, SUBTITULO);
    }

    private PdfPTable tabla(float[] anchos, String... cabeceras) throws DocumentException {
        PdfPTable tabla = new PdfPTable(anchos.length);
        tabla.setWidthPercentage(100);
        tabla.setWidths(anchos);
        tabla.setHeaderRows(1);
        for (String cabecera : cabeceras) {
            PdfPCell celda = new PdfPCell(new Phrase(cabecera, CABECERA));
            celda.setHorizontalAlignment(Element.ALIGN_LEFT);
            celda.setPadding(4);
            tabla.addCell(celda);
        }
        return tabla;
    }

    private void celda(PdfPTable tabla, String texto) {
        PdfPCell celda = new PdfPCell(new Phrase(texto == null ? "" : texto, CELDA));
        celda.setPadding(4);
        tabla.addCell(celda);
    }

    /** Lo que cada PDF escribe dentro del documento ya abierto. */
    private interface Contenido {
        void escribir(Document documento) throws DocumentException;
    }
}
