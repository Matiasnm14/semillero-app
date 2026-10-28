package edu.upb.semillero.controller.torneos;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Un torneo armado de punta a punta por la API, para las pruebas del sprint 3:
 * reglamento, categoría Sub-14, equipos inscritos y aprobados, cada uno con su
 * lista de buena fe, y dos canchas. El fixture se genera aparte, porque cada
 * prueba lo quiere distinto.
 *
 * <p>Las fechas se calculan desde hoy, igual que la colección de Postman, para
 * que las pruebas no se venzan.
 */
final class EscenarioTorneo {
    static final String TORNEOS = "/api/v1/torneos";
    static final String INSCRIPCIONES = "/api/v1/torneos/inscripciones";

    final MockMvc mvc;
    final ObjectMapper json;
    final long usuarioId;
    final long canchaA;
    final long canchaB;
    final long torneoId;
    final long categoriaId;
    final LocalDate primeraJornada;
    final List<Equipo> equipos = new ArrayList<>();
    private final long ciudadId;
    private final int temporada;

    /** Un club inscrito y aprobado, con los IDs de los jugadores de su lista en orden de dorsal. */
    record Equipo(long clubId, long inscripcionId, List<Long> jugadores) {}

    private EscenarioTorneo(MockMvc mvc, ObjectMapper json, String formato, boolean externo,
            List<String> desempates) throws Exception {
        this.mvc = mvc;
        this.json = json;
        LocalDate hoy = LocalDate.now();
        temporada = hoy.getYear();
        primeraJornada = hoy.plusDays(30);
        long paisId = create("/api/v1/paises", Map.of("nombrePais", "Bolivia"));
        ciudadId = create("/api/v1/ciudades", Map.of("nombreCiudad", "Santa Cruz", "paisId", paisId));
        usuarioId = usuario("org");
        long sedeId = create(TORNEOS + "/sedes", Map.of("nombre", "Sede " + System.nanoTime(),
                "direccion", "Av. Banzer 100", "ciudadId", ciudadId));
        canchaA = cancha(sedeId, "Cancha A");
        canchaB = cancha(sedeId, "Cancha B");
        torneoId = torneo(formato, externo);
        categoriaId = categoria();
        reglamento(desempates);
        mvc.perform(post(TORNEOS + "/" + torneoId + "/abrir-inscripciones"))
                .andExpect(status().isOk());
    }

    /** Liga interna con {@code equipos} equipos de {@code jugadores} jugadores cada uno. */
    static EscenarioTorneo liga(MockMvc mvc, ObjectMapper json, int equipos, int jugadores)
            throws Exception {
        return con(mvc, json, "LIGA", false, equipos, jugadores,
                List.of("DIF_GOLES", "GOLES_FAVOR", "ENFRENTAMIENTO", "FAIR_PLAY", "SORTEO"));
    }

    static EscenarioTorneo con(MockMvc mvc, ObjectMapper json, String formato, boolean externo,
            int equipos, int jugadores, List<String> desempates) throws Exception {
        EscenarioTorneo escenario = new EscenarioTorneo(mvc, json, formato, externo, desempates);
        for (int i = 0; i < equipos; i++) {
            escenario.equipoInscrito(jugadores);
        }
        return escenario;
    }

    /** Inscribe, aprueba y carga la lista de buena fe de un club nuevo. */
    Equipo equipoInscrito(int jugadores) throws Exception {
        return equipoInscrito(jugadores, null);
    }

    /** Lo mismo, dentro de un grupo, para el formato de grupos con fase final. */
    Equipo equipoInscrito(int jugadores, Long grupoId) throws Exception {
        long clubId = club("Club " + System.nanoTime() % 100000);
        Map<String, Object> inscripcion = new LinkedHashMap<>();
        inscripcion.put("torneoCategoriaId", categoriaId);
        inscripcion.put("clubId", clubId);
        inscripcion.put("usuarioId", usuarioId);
        if (grupoId != null) {
            inscripcion.put("grupoId", grupoId);
        }
        long inscripcionId = create(INSCRIPCIONES, inscripcion);
        Map<String, Object> cambio = new LinkedHashMap<>();
        cambio.put("estado", "APROBADA");
        cambio.put("usuarioId", usuarioId);
        putJson(INSCRIPCIONES + "/" + inscripcionId + "/estado", cambio).andExpect(status().isOk());
        List<Long> enLista = new ArrayList<>();
        for (int dorsal = 1; dorsal <= jugadores; dorsal++) {
            long jugadorId = jugadorFichado(clubId);
            create(INSCRIPCIONES + "/" + inscripcionId + "/lista-buena-fe",
                    Map.of("jugadorId", jugadorId, "dorsal", dorsal));
            enLista.add(jugadorId);
        }
        Equipo equipo = new Equipo(clubId, inscripcionId, enLista);
        equipos.add(equipo);
        return equipo;
    }

    /**
     * Suma a la lista de buena fe del equipo un jugador con cuenta de usuario,
     * que es con la que entra a la app (HU 14). Devuelve el ID del jugador.
     */
    long jugadorConCuenta(Equipo equipo, long usuarioDelJugador, int dorsal) throws Exception {
        Map<String, Object> jugador = new LinkedHashMap<>();
        jugador.put("usuarioId", usuarioDelJugador);
        jugador.put("piernaHabil", "I");
        jugador.put("fechaNacimiento", LocalDate.of(temporada - 13, 3, 1).toString());
        long jugadorId = create("/api/v1/jugadores", jugador);
        Map<String, Object> fichaje = new LinkedHashMap<>();
        fichaje.put("fechaFichaje", LocalDate.of(temporada - 1, 1, 10).atStartOfDay().toString());
        fichaje.put("jugadorId", jugadorId);
        fichaje.put("clubId", equipo.clubId());
        create("/api/v1/fichados", fichaje);
        create(INSCRIPCIONES + "/" + equipo.inscripcionId() + "/lista-buena-fe",
                Map.of("jugadorId", jugadorId, "dorsal", dorsal));
        equipo.jugadores().add(jugadorId);
        return jugadorId;
    }

    /** El primer partido del fixture donde juega el club, después de {@code despuesDe}. */
    JsonNode partidoDe(JsonNode fixture, long clubId, long despuesDe) {
        for (JsonNode partido : fixture) {
            long id = partido.get("id").asLong();
            if (id != despuesDe && (partido.get("clubLocalId").asLong() == clubId
                    || partido.get("clubVisitanteId").asLong() == clubId)
                    && (despuesDe == 0 || partido.get("jornada").asInt() > jornadaDe(fixture, despuesDe))) {
                return partido;
            }
        }
        throw new AssertionError("El club " + clubId + " no tiene otro partido en el fixture");
    }

    private int jornadaDe(JsonNode fixture, long partidoId) {
        for (JsonNode partido : fixture) {
            if (partido.get("id").asLong() == partidoId) {
                return partido.get("jornada").asInt();
            }
        }
        return 0;
    }

    /** Club que juega pero no está inscrito: el rival de un campeonato externo. */
    long clubSinInscribir() throws Exception {
        return club("Rival " + System.nanoTime() % 100000);
    }

    String fixture() {
        return TORNEOS + "/" + torneoId + "/categorias/" + categoriaId + "/fixture";
    }

    String tabla() {
        return TORNEOS + "/" + torneoId + "/categorias/" + categoriaId + "/tabla";
    }

    /** Genera la liga en las dos canchas y devuelve los partidos en el orden del fixture. */
    JsonNode generarLiga() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fechaPrimeraJornada", primeraJornada.toString());
        body.put("horaPrimerPartido", "09:00:00");
        body.put("diasEntreJornadas", 7);
        body.put("canchaIds", List.of(canchaA, canchaB));
        body.put("usuarioId", usuarioId);
        postJson(fixture() + "/liga", body).andExpect(status().isCreated());
        return leer(get(fixture()));
    }

    Equipo equipoDe(long clubId) {
        return equipos.stream().filter(e -> e.clubId() == clubId).findFirst().orElseThrow();
    }

    String partido(long partidoId) {
        return TORNEOS + "/partidos/" + partidoId;
    }

    /**
     * Guarda la planilla con los primeros {@code titulares} de cada equipo como
     * titulares, el resto suplentes, y el primero de cada uno de capitán.
     */
    ResultActions planillaCompleta(long partidoId, long local, long visitante, int titulares)
            throws Exception {
        List<Map<String, Object>> jugadores = new ArrayList<>();
        for (long club : List.of(local, visitante)) {
            List<Long> lista = equipoDe(club).jugadores();
            for (int i = 0; i < lista.size(); i++) {
                jugadores.add(jugador(lista.get(i), i < titulares, i == 0));
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jugadores", jugadores);
        body.put("cuerpoTecnico", List.of(
                Map.of("clubId", local, "nombreCompleto", "Ana Torres", "cargo", "DT"),
                Map.of("clubId", visitante, "nombreCompleto", "Luis Rojas", "cargo", "DT")));
        body.put("usuarioId", usuarioId);
        return putJson(partido(partidoId) + "/planilla", body);
    }

    void confirmarPlanilla(long partidoId) throws Exception {
        mvc.perform(post(partido(partidoId) + "/planilla/confirmar")
                        .param("usuarioId", String.valueOf(usuarioId)))
                .andExpect(status().isOk());
    }

    static Map<String, Object> jugador(long jugadorId, boolean titular, boolean capitan) {
        Map<String, Object> jugador = new LinkedHashMap<>();
        jugador.put("jugadorId", jugadorId);
        jugador.put("esTitular", titular);
        jugador.put("capitan", capitan);
        return jugador;
    }

    ResultActions evento(long partidoId, String tipo, int minuto, long jugadorId, Long relacionado,
            String tipoGol, String motivo) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tipo", tipo);
        body.put("minuto", minuto);
        body.put("jugadorId", jugadorId);
        if (relacionado != null) {
            body.put("jugadorRelacionadoId", relacionado);
        }
        if (tipoGol != null) {
            body.put("tipoGol", tipoGol);
        }
        if (motivo != null) {
            body.put("motivo", motivo);
        }
        body.put("usuarioId", usuarioId);
        return postJson(partido(partidoId) + "/eventos", body);
    }

    ResultActions cerrar(long partidoId, Map<String, Object> extra) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>(extra);
        body.put("usuarioId", usuarioId);
        return postJson(partido(partidoId) + "/cerrar", body);
    }

    JsonNode leer(org.springframework.test.web.servlet.RequestBuilder pedido) throws Exception {
        return json.readTree(mvc.perform(pedido).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString());
    }

    long usuario(String nombre) throws Exception {
        return create("/api/v1/usuarios", Map.of("name", nombre, "nombres", "Prueba",
                "apellidos", "Torneos", "email", nombre + System.nanoTime() + "@example.com",
                "password", "secreto123"));
    }

    ResultActions postJson(String url, Map<String, Object> body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    ResultActions putJson(String url, Map<String, Object> body) throws Exception {
        return mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    long create(String url, Map<String, Object> body) throws Exception {
        return json.readTree(postJson(url, body).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString()).get("id").asLong();
    }

    /** Jugador de 13 años a la fecha de corte, fichado en el club desde el año pasado. */
    private long jugadorFichado(long clubId) throws Exception {
        Map<String, Object> jugador = new LinkedHashMap<>();
        jugador.put("piernaHabil", "D");
        jugador.put("fechaNacimiento", LocalDate.of(temporada - 13, 6, 1).toString());
        long jugadorId = create("/api/v1/jugadores", jugador);
        Map<String, Object> fichaje = new LinkedHashMap<>();
        fichaje.put("fechaFichaje", LocalDate.of(temporada - 1, 1, 10).atStartOfDay().toString());
        fichaje.put("jugadorId", jugadorId);
        fichaje.put("clubId", clubId);
        create("/api/v1/fichados", fichaje);
        return jugadorId;
    }

    private long torneo(String formato, boolean externo) throws Exception {
        LocalDate hoy = LocalDate.now();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nombre", "Copa Sprint 3");
        body.put("temporada", temporada);
        body.put("tipo", "OFICIAL");
        body.put("ambito", externo ? "EXTERNO" : "INTERNO");
        if (externo) {
            body.put("organizadorExterno", "Liga Cruceña");
        }
        body.put("formato", formato);
        body.put("fechaInicio", hoy.plusDays(20).toString());
        body.put("fechaFin", hoy.plusDays(200).toString());
        body.put("fechaLimiteInscripcion", hoy.plusDays(10).toString());
        body.put("usuarioId", usuarioId);
        return create(TORNEOS, body);
    }

    private long categoria() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nombre", "Sub-14");
        body.put("edadMinima", 12);
        body.put("edadMaxima", 14);
        body.put("cupoMaximo", 8);
        body.put("duracionTiempoMin", 35);
        body.put("jugadoresEnCancha", 7);
        body.put("maxSustituciones", 2);
        return create(TORNEOS + "/" + torneoId + "/categorias", body);
    }

    private void reglamento(List<String> desempates) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fechaCorte", LocalDate.of(temporada, 12, 31).toString());
        body.put("minJugadores", 2);
        body.put("maxJugadores", 10);
        body.put("desempates", desempates);
        body.put("usuarioId", usuarioId);
        postJson(TORNEOS + "/" + torneoId + "/reglamento", body).andExpect(status().isCreated());
    }

    private long cancha(long sedeId, String nombre) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sedeId", sedeId);
        body.put("nombre", nombre);
        body.put("tipoSuperficie", "CESPED_SINTETICO");
        body.put("descripcion", "100x60, iluminada");
        return create(TORNEOS + "/canchas", body);
    }

    private long club(String nombre) throws Exception {
        return create("/api/v1/clubes", Map.of("nombreClub", nombre, "ciudadId", ciudadId));
    }
}
