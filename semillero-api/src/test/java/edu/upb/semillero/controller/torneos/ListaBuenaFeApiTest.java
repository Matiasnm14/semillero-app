package edu.upb.semillero.controller.torneos;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HU 5 - Lista de buena fe de punta a punta: tareas S2-06 (alta), S2-07
 * (elegibilidad) y S2-08 (mínimo y máximo).
 *
 * <p>El jugador y su fichaje se cargan por el CRUD que ya existe, así que esto
 * también comprueba la tarea S2-05: {@code FichaTecnicaService} lee de verdad
 * {@code jugadores} y {@code fichado}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ListaBuenaFeApiTest {
    private static final String TORNEOS = "/api/v1/torneos";
    private static final String INSCRIPCIONES = "/api/v1/torneos/inscripciones";
    /** El reglamento corta al 31/12 de la temporada: la edad se calcula a esa fecha. */
    private static final String FECHA_CORTE = "2026-12-31";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private long ciudadId;
    private long usuarioId;
    private long clubId;
    private long torneoId;
    private long categoriaId;
    private long inscripcionId;

    @BeforeEach
    void fixtures() throws Exception {
        long paisId = create("/api/v1/paises", Map.of("nombrePais", "Bolivia"));
        ciudadId = create("/api/v1/ciudades", Map.of("nombreCiudad", "Santa Cruz", "paisId", paisId));
        usuarioId = create("/api/v1/usuarios", Map.of("name", "dt",
                "nombres", "Miguel", "apellidos", "Lopez",
                "email", "buenafe" + System.nanoTime() + "@example.com",
                "password", "secreto123"));
        clubId = club("Club Lista");
        torneoId = torneo();
        categoriaId = categoria();
        reglamento(3, 5);
        mvc.perform(post(TORNEOS + "/" + torneoId + "/abrir-inscripciones"))
                .andExpect(status().isOk());
        inscripcionId = inscripcion(clubId);
    }

    @Test
    void agregaUnJugadorHabilitadoYLoListaConSuDorsal() throws Exception {
        long jugadorId = jugadorFichado(clubId, "2013-06-01");

        long id = idDe(postJson(lista(), jugador(jugadorId, 10))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.jugadorId").value(jugadorId))
                .andExpect(jsonPath("$.dorsal").value(10))
                .andExpect(jsonPath("$.torneoId").value(torneoId))
                .andExpect(jsonPath("$.habilitado").value(true))
                .andExpect(jsonPath("$.motivoInhabilitacion").doesNotExist()));

        mvc.perform(get(lista())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].jugadorId").value(jugadorId));
        mvc.perform(get(lista() + "/" + id)).andExpect(status().isOk());
        mvc.perform(delete(lista() + "/" + id)).andExpect(status().isNoContent());
        mvc.perform(get(lista() + "/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void noRepiteElDorsalDentroDelEquipo() throws Exception {
        long primero = jugadorFichado(clubId, "2013-06-01");
        long segundo = jugadorFichado(clubId, "2013-09-15");

        postJson(lista(), jugador(primero, 7)).andExpect(status().isCreated());
        postJson(lista(), jugador(segundo, 7)).andExpect(status().isConflict());
        postJson(lista(), jugador(segundo, 8)).andExpect(status().isCreated());
    }

    @Test
    void unJugadorEstaEnUnSoloEquipoDelTorneo() throws Exception {
        long jugadorId = jugadorFichado(clubId, "2013-06-01");
        postJson(lista(), jugador(jugadorId, 5)).andExpect(status().isCreated());

        long otroClub = club("Club Rival");
        fichaje(jugadorId, otroClub);
        long otraInscripcion = inscripcion(otroClub);

        postJson(lista(otraInscripcion), jugador(jugadorId, 5)).andExpect(status().isConflict());
    }

    @Test
    void rechazaAlJugadorFueraDelRangoDeEdadALaFechaDeCorte() throws Exception {
        long grande = jugadorFichado(clubId, "2008-06-01");

        postJson(lista(), jugador(grande, 9))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("años al " + FECHA_CORTE)));
    }

    @Test
    void rechazaAlJugadorQueNoEstaFichadoEnElClub() throws Exception {
        long sinFichaje = jugador("2013-06-01");

        postJson(lista(), jugador(sinFichaje, 4))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("no está fichado en el club")));
    }

    @Test
    void rechazaAlJugadorSinFechaDeNacimiento() throws Exception {
        long sinFecha = jugadorFichado(clubId, null);

        postJson(lista(), jugador(sinFecha, 3))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("fecha de nacimiento")));
    }

    @Test
    void confirmaLaListaSoloConElMinimoYDespuesNoLaDejaModificar() throws Exception {
        String confirmar = lista() + "/confirmar";
        postJson(lista(), jugador(jugadorFichado(clubId, "2013-06-01"), 1))
                .andExpect(status().isCreated());
        postJson(lista(), jugador(jugadorFichado(clubId, "2013-07-01"), 2))
                .andExpect(status().isCreated());

        mvc.perform(post(confirmar)).andExpect(status().isBadRequest());

        postJson(lista(), jugador(jugadorFichado(clubId, "2013-08-01"), 3))
                .andExpect(status().isCreated());
        mvc.perform(post(confirmar)).andExpect(status().isOk())
                .andExpect(jsonPath("$.listaConfirmada").value(true))
                .andExpect(jsonPath("$.totalJugadores").value(3))
                .andExpect(jsonPath("$.minJugadores").value(3))
                .andExpect(jsonPath("$.maxJugadores").value(5));

        mvc.perform(get(INSCRIPCIONES + "/" + inscripcionId))
                .andExpect(jsonPath("$.listaConfirmada").value(true));
        postJson(lista(), jugador(jugadorFichado(clubId, "2013-09-01"), 4))
                .andExpect(status().isConflict());
        mvc.perform(post(confirmar)).andExpect(status().isConflict());
    }

    @Test
    void noPasaDelMaximoDeJugadoresDelReglamento() throws Exception {
        for (int dorsal = 1; dorsal <= 5; dorsal++) {
            postJson(lista(), jugador(jugadorFichado(clubId, "2013-06-01"), dorsal))
                    .andExpect(status().isCreated());
        }
        postJson(lista(), jugador(jugadorFichado(clubId, "2013-06-01"), 6))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validaLaInscripcionYElCuerpoDelPedido() throws Exception {
        long jugadorId = jugadorFichado(clubId, "2013-06-01");

        mvc.perform(get(lista(Long.MAX_VALUE))).andExpect(status().isNotFound());
        postJson(lista(Long.MAX_VALUE), jugador(jugadorId, 10)).andExpect(status().isNotFound());
        postJson(lista(), jugador(Long.MAX_VALUE, 10)).andExpect(status().isNotFound());
        postJson(lista(), jugador(jugadorId, 0)).andExpect(status().isBadRequest());
        postJson(lista(), jugador(jugadorId, 100)).andExpect(status().isBadRequest());

        Map<String, Object> sinJugador = new LinkedHashMap<>();
        sinJugador.put("dorsal", 10);
        postJson(lista(), sinJugador).andExpect(status().isBadRequest());
    }

    private String lista() {
        return lista(inscripcionId);
    }

    private String lista(long inscripcion) {
        return INSCRIPCIONES + "/" + inscripcion + "/lista-buena-fe";
    }

    private Map<String, Object> jugador(long jugadorId, int dorsal) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jugadorId", jugadorId);
        body.put("dorsal", dorsal);
        return body;
    }

    private long jugadorFichado(long club, String fechaNacimiento) throws Exception {
        long jugadorId = jugador(fechaNacimiento);
        fichaje(jugadorId, club);
        return jugadorId;
    }

    private long jugador(String fechaNacimiento) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("piernaHabil", "D");
        if (fechaNacimiento != null) {
            body.put("fechaNacimiento", fechaNacimiento);
        }
        return create("/api/v1/jugadores", body);
    }

    /** Fichaje abierto: empezó antes de la fecha de corte y no tiene fecha de fin. */
    private void fichaje(long jugadorId, long club) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fechaFichaje", "2026-01-10T00:00:00");
        body.put("jugadorId", jugadorId);
        body.put("clubId", club);
        create("/api/v1/fichados", body);
    }

    private long torneo() throws Exception {
        LocalDate hoy = LocalDate.now();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nombre", "Copa Buena Fe");
        body.put("temporada", 2026);
        body.put("tipo", "OFICIAL");
        body.put("ambito", "INTERNO");
        body.put("formato", "LIGA");
        body.put("fechaInicio", hoy.plusDays(20).toString());
        body.put("fechaFin", hoy.plusDays(100).toString());
        body.put("fechaLimiteInscripcion", hoy.plusDays(10).toString());
        body.put("usuarioId", usuarioId);
        return create(TORNEOS, body);
    }

    private long categoria() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nombre", "Sub-14");
        body.put("edadMinima", 12);
        body.put("edadMaxima", 14);
        body.put("cupoMaximo", 4);
        body.put("duracionTiempoMin", 35);
        body.put("jugadoresEnCancha", 11);
        return create(TORNEOS + "/" + torneoId + "/categorias", body);
    }

    private void reglamento(int minimo, int maximo) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fechaCorte", FECHA_CORTE);
        body.put("minJugadores", minimo);
        body.put("maxJugadores", maximo);
        body.put("desempates", List.of("DIF_GOLES", "GOLES_FAVOR"));
        body.put("usuarioId", usuarioId);
        postJson(TORNEOS + "/" + torneoId + "/reglamento", body)
                .andExpect(status().isCreated());
    }

    private long inscripcion(long club) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("torneoCategoriaId", categoriaId);
        body.put("clubId", club);
        body.put("usuarioId", usuarioId);
        return create(INSCRIPCIONES, body);
    }

    private long club(String nombre) throws Exception {
        return create("/api/v1/clubes", Map.of("nombreClub", nombre, "ciudadId", ciudadId));
    }

    private long create(String url, Map<String, Object> body) throws Exception {
        return idDe(postJson(url, body).andExpect(status().isCreated()));
    }

    private long idDe(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString())
                .get("id").asLong();
    }

    private ResultActions postJson(String url, Map<String, Object> body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }
}
