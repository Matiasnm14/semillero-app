package edu.upb.semillero.controller.torneos;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HU 15 - Tarea S4-09: al cerrar un partido, torneos le manda
 * {@code PartidoFinalizado} al microservicio de video por REST, con el contrato
 * mínimo del punto 13 del acuerdo. El microservicio acá es un servidor HTTP de
 * prueba que guarda lo que recibe.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "ORGANIZADOR")
class PartidoFinalizadoApiTest {
    private static final BlockingQueue<Recibido> RECIBIDOS = new LinkedBlockingQueue<>();
    private static final HttpServer VIDEO = microservicioDeVideo();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private record Recibido(String metodo, String autorizacion, String cuerpo) {}

    @DynamicPropertySource
    static void apuntarAlMicroservicio(DynamicPropertyRegistry propiedades) {
        propiedades.add("semillero.torneos.partido-finalizado-url",
                () -> "http://127.0.0.1:" + VIDEO.getAddress().getPort() + "/internal/v1/partidos-finalizados");
        propiedades.add("semillero.torneos.partido-finalizado-token", () -> "token-del-microservicio");
    }

    @AfterAll
    static void apagar() {
        VIDEO.stop(0);
    }

    @Test
    void alCerrarElPartidoSeAvisaAlMicroservicioDeVideo() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 2, 2);
        JsonNode partido = torneo.generarLiga().get(0);
        long partidoId = partido.get("id").asLong();
        RECIBIDOS.clear();

        String fechaCierre = json.readTree(torneo.cerrar(partidoId,
                        Map.of("ganadorWalkoverId", partido.get("clubLocalId").asLong()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("fechaCierre").asText();

        Recibido recibido = RECIBIDOS.poll(10, TimeUnit.SECONDS);
        assertThat(recibido).withFailMessage("El microservicio de video no recibió el aviso").isNotNull();
        assertThat(recibido.metodo()).isEqualTo("POST");
        assertThat(recibido.autorizacion()).isEqualTo("Bearer token-del-microservicio");
        JsonNode cuerpo = json.readTree(recibido.cuerpo());
        assertThat(cuerpo.get("partidoId").asLong()).isEqualTo(partidoId);
        assertThat(cuerpo.get("fechaCierre").asText()).startsWith(fechaCierre.substring(0, 19));
        // Contrato mínimo: nada más que esos dos campos.
        assertThat(cuerpo.size()).isEqualTo(2);
    }

    @Test
    void siElCierreFallaNoSeAvisa() throws Exception {
        EscenarioTorneo torneo = EscenarioTorneo.liga(mvc, json, 2, 2);
        long partidoId = torneo.generarLiga().get(0).get("id").asLong();
        RECIBIDOS.clear();

        // Sin planilla confirmada el cierre se rechaza: no hay nada que avisar.
        torneo.cerrar(partidoId, Map.of()).andExpect(status().isBadRequest());

        assertThat(RECIBIDOS.poll(1, TimeUnit.SECONDS)).isNull();
    }

    private static HttpServer microservicioDeVideo() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/internal/v1/partidos-finalizados", intercambio -> {
                String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                RECIBIDOS.add(new Recibido(intercambio.getRequestMethod(),
                        intercambio.getRequestHeaders().getFirst("Authorization"), cuerpo));
                intercambio.sendResponseHeaders(202, -1);
                intercambio.close();
            });
            servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo levantar el microservicio de prueba", e);
        }
    }
}
